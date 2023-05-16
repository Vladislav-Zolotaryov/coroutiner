package coroutiner

import coroutiner.setup.*
import io.r2dbc.spi.Connection
import io.r2dbc.spi.Row
import kotlinx.coroutines.reactive.awaitSingle
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.LocalDateTime

open class R2dbcBenchmark {
    
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    fun singleRecordWhere(postgreState: PostgreState) {
        routine {
            Mono.usingWhen(
                postgreState.connectionPool.create(),
                { connection ->
                    Flux.fromIterable(postgreState.singleQueryUserIds).flatMap { id ->
                        connection.createStatement("${postgreState.baseQuery} WHERE u.id = $1")
                            .bind("$1", id)
                            .execute()
                            .toFlux()
                            .flatMap { result ->
                                result.map { row, _ ->
                                    row.fullUser()
                                }
                            }
                    }.collectList()
                },
                Connection::close,
            ).awaitSingle()
                .let {
                    assert(it.size == postgreState.singleQueryUserIds.size)
                }
        }
    }
    
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    fun multiRecordWhere(postgreState: PostgreState) {
        routine {
            Mono.usingWhen(
                postgreState.connectionPool.create(),
                { connection ->
                    val paramsPlaceholders =
                        (1..postgreState.singleQueryUserIds.size).joinToString(separator = ", ") { "$$it" }
                    
                    val statement =
                        connection.createStatement("${postgreState.baseQuery} WHERE u.id IN ($paramsPlaceholders)")
                    
                    postgreState.singleQueryUserIds.withIndex().forEach { (index, id) ->
                        statement.bind("$${index + 1}", id)
                    }
                    
                    statement.execute()
                        .toFlux()
                        .flatMap { result ->
                            result.map { row, _ ->
                                row.fullUser()
                            }
                        }.collectList()
                },
                Connection::close,
            ).awaitSingle()
                .let {
                    assert(it.size == postgreState.singleQueryUserIds.size)
                }
        }
    }
    
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    fun largeQueryWithLimit(postgreState: PostgreState) {
        routine {
            Mono.usingWhen(
                postgreState.connectionPool.create(),
                { connection ->
                    connection.createStatement("${postgreState.baseQuery} LIMIT $1")
                        .bind("$1", BenchmarkConfig.recordQueryLimit)
                        .execute()
                        .toFlux()
                        .flatMap { result ->
                            result.map { row, _ ->
                                row.fullUser()
                            }
                        }.collectList()
                },
                Connection::close
            )
                .awaitSingle()
                .let {
                    assert(it.size == BenchmarkConfig.recordQueryLimit)
                }
        }
    }
    
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    fun fullScan(postgreState: PostgreState) {
        routine {
            Mono.usingWhen(
                postgreState.connectionPool.create(),
                { connection ->
                    connection.createStatement(postgreState.baseQuery)
                        .execute()
                        .toFlux()
                        .flatMap { result ->
                            result.map { row, _ ->
                                row.fullUser()
                            }
                        }.collectList()
                },
                Connection::close
            )
                .awaitSingle()
                .let {
                    assert(it.size == BenchmarkConfig.userRecordCount)
                }
        }
    }
}


// Dev Runner
fun main() {
    PostgreState()
        .open()
        .use { state ->
            with(R2dbcBenchmark()) {
                repeat(30) { singleRecordWhere(state) }
                multiRecordWhere(state)
                largeQueryWithLimit(state)
                fullScan(state)
            }
        }
}

fun Row.fullUser() = UserFull(
    id = get("user_id") as Long,
    name = get("user_name") as String,
    groupId = get("group_id") as Long,
    groupName = get("group_name") as String,
    created = get("user_created") as LocalDateTime,
    updated = get("user_updated") as LocalDateTime,
)

fun <T> Publisher<T>.toFlux() = Flux.from(this)
