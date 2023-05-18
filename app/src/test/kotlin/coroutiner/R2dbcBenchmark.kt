package coroutiner

import coroutiner.setup.*
import io.r2dbc.spi.Connection
import io.r2dbc.spi.Row
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import org.openjdk.jmh.annotations.*
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.LocalDateTime
import kotlin.system.measureNanoTime
import kotlin.time.Duration.Companion.nanoseconds

open class R2dbcBenchmark {
    
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    suspend fun singleRecordWhere(postgreState: PostgreState) {
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
                    require(it.size == postgreState.singleQueryUserIds.size)
                }
    }
    
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
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
                    require(it.size == postgreState.singleQueryUserIds.size)
                }
        }
    }
    
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
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
                    require(it.size == BenchmarkConfig.recordQueryLimit)
                }
        }
    }
    
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
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
                    require(it.size == BenchmarkConfig.userRecordCount)
                }
        }
    }
}


// Dev Runner
fun r2dbcRun() {
    PostgreState()
        .open()
        .use { state ->
            with(R2dbcBenchmark()) {
                runBlocking {
                    singleRecordWhere(state)
                    measureNanoTime {
                        singleRecordWhere(state)
                    }.let { println("R2DBC singleRecordWhere took ${it.nanoseconds / 5} per operation") }
                    
                    multiRecordWhere(state)
                    measureNanoTime {
                        multiRecordWhere(state)
                    }.let { println("R2DBC multiRecordWhere took ${it.nanoseconds / 5} per operation") }
                    
                    largeQueryWithLimit(state)
                    measureNanoTime {
                        largeQueryWithLimit(state)
                    }.let { println("R2DBC largeQueryWithLimit took ${it.nanoseconds / 5} per operation") }
                    
                    fullScan(state)
                    measureNanoTime {
                        fullScan(state)
                    }.let { println("R2DBC fullScan took ${it.nanoseconds / 5} per operation") }
                }
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
