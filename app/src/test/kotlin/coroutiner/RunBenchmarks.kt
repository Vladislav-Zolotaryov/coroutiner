package coroutiner

import coroutiner.benchmarks.JdbcBenchmark
import coroutiner.benchmarks.R2dbcBenchmark
import coroutiner.benchmarks.VertxPgClientBenchmark
import coroutiner.setup.BenchmarkConfig
import coroutiner.setup.BenchmarkConfig.iterationsPerBenchmark
import coroutiner.setup.PostgresContainerState
import coroutiner.setup.db.JdbcState
import coroutiner.setup.db.R2dbcState
import coroutiner.setup.db.VertxPgState
import io.vertx.kotlin.coroutines.await
import kotlinx.coroutines.*
import kotlinx.coroutines.reactor.awaitSingle
import kotlin.system.measureNanoTime
import kotlin.time.Duration.Companion.nanoseconds


fun main() {
    runBlocking {
        val postgresContainerState = PostgresContainerState()
        postgresContainerState.open().use { pgContainerState ->
            VertxPgState(pgContainerState).use {
                it.setupBenchmarkData()
            }
            
            launch(Dispatchers.IO) {
                repeat(BenchmarkConfig.runs) {
                    JdbcState(postgresContainerState)
                        .use { state ->
                            val jdbcBenchmark = JdbcBenchmark()
                            (1..iterationsPerBenchmark).map {
                                async {
                                    measureNanoTime {
                                        jdbcBenchmark.singleRecordWhere(state, BenchmarkConfig.randomUserId())
                                    }.nanoseconds
                                }
                            }.awaitAll()
                                .reduce { left, right -> left.plus(right) }
                                .let {
                                    println("JDBC singleRecordWhere took average ${it.div(iterationsPerBenchmark)} per operation")
                                }
                        }
                    
                    VertxPgState(postgresContainerState)
                        .use { state ->
                            val vertxPgClientBenchmark = VertxPgClientBenchmark()
                            (1..iterationsPerBenchmark).map {
                                async {
                                    measureNanoTime {
                                        vertxPgClientBenchmark.singleRecordWhere(state, BenchmarkConfig.randomUserId())
                                            .await()
                                    }.nanoseconds
                                }
                            }.awaitAll()
                                .reduce { left, right -> left.plus(right) }
                                .let {
                                    println("VertX singleRecordWhere took average ${it.div(iterationsPerBenchmark)} per operation")
                                }
                        }
                    
                    R2dbcState(postgresContainerState)
                        .use { state ->
                            val r2dbcBenchmark = R2dbcBenchmark()
                            (1..iterationsPerBenchmark).map {
                                async {
                                    measureNanoTime {
                                        r2dbcBenchmark.singleRecordWhere(state, BenchmarkConfig.randomUserId())
                                            .awaitSingle()
                                    }.nanoseconds
                                }
                            }.awaitAll()
                                .reduce { left, right -> left.plus(right) }
                                .let {
                                    println("R2DBC singleRecordWhere took average ${it.div(iterationsPerBenchmark)} per operation")
                                }
                        }
                }
            }.join()
        }
    }
}