package coroutiner

import coroutiner.setup.*
import io.vertx.kotlin.coroutines.await
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.Tuple
import kotlinx.coroutines.runBlocking
import org.openjdk.jmh.annotations.*
import java.time.LocalDateTime
import kotlin.system.measureNanoTime
import kotlin.time.Duration.Companion.nanoseconds

open class VertxPgClientBenchmark {
    
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    suspend fun singleRecordWhere(postgreState: PostgreState) {
        postgreState.pgClient.let { client ->
            val query = client
                .preparedQuery("${postgreState.baseQuery} WHERE u.id=$1")
            
            postgreState.singleQueryUserIds
                .map { Tuple.of(it) }
                .flatMap { tuple ->
                    query.execute(tuple)
                        .map {
                            it.map { row ->
                                row.fullUser()
                            }
                        }
                        .await()
                }
                .let {
                    require(it.size == postgreState.singleQueryUserIds.size)
                }
        }
    }
    
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    fun multiRecordWhere(postgreState: PostgreState) {
        routine {
            postgreState.pgClient.let { client ->
                val bindingString =
                    (1..postgreState.singleQueryUserIds.size).joinToString(separator = ", ") { "$$it" }
                
                client
                    .preparedQuery("${postgreState.baseQuery} WHERE u.id IN ($bindingString)")
                    .execute(Tuple.tuple(postgreState.singleQueryUserIds))
                    .map {
                        it.map { row ->
                            row.fullUser()
                        }
                    }
                    .await()
                    .let {
                        require(it.size == postgreState.singleQueryUserIds.size)
                    }
            }
        }
    }
    
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    fun largeQueryWithLimit(postgreState: PostgreState) {
        routine {
            postgreState.pgClient.let { client ->
                client
                    .query("${postgreState.baseQuery} LIMIT ${BenchmarkConfig.recordQueryLimit}")
                    .execute()
                    .map {
                        it.map { row ->
                            row.fullUser()
                        }
                    }
                    .await()
                    .let {
                        require(it.size == BenchmarkConfig.recordQueryLimit)
                    }
            }
        }
    }
    
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    fun fullScan(postgreState: PostgreState) {
        routine {
            postgreState.pgClient.let { client ->
                client
                    .query(postgreState.baseQuery)
                    .execute()
                    .map {
                        it.map { row ->
                            row.fullUser()
                        }
                    }
                    .await()
                    .let {
                        require(it.size == BenchmarkConfig.userRecordCount)
                    }
            }
        }
    }
}


// Dev Runner
fun vertxRun() {
    PostgreState()
        .open()
        .use { state ->
            runBlocking {
                with(VertxPgClientBenchmark()) {
                    singleRecordWhere(state)
                    measureNanoTime {
                        singleRecordWhere(state)
                    }.let { println("Vertx singleRecordWhere took ${it.nanoseconds / 5} per operation") }
                    
                    multiRecordWhere(state)
                    measureNanoTime {
                        multiRecordWhere(state)
                    }.let { println("Vertx multiRecordWhere took ${it.nanoseconds / 5} per operation") }
                    
                    largeQueryWithLimit(state)
                    measureNanoTime {
                        largeQueryWithLimit(state)
                    }.let { println("Vertx largeQueryWithLimit took ${it.nanoseconds / 5} per operation") }
                    
                    fullScan(state)
                    measureNanoTime {
                        fullScan(state)
                    }.let { println("Vertx fullScan took ${it.nanoseconds / 5} per operation") }
                }
            }
        }
}

fun Row.fullUser() = UserFull(
    id = this.getLong("user_id"),
    name = this.getString("user_name"),
    groupId = this.getLong("group_id"),
    groupName = this.getString("group_name"),
    created = this.get(LocalDateTime::class.java, "user_created"),
    updated = this.get(LocalDateTime::class.java, "user_updated"),
)