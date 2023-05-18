package coroutiner

import coroutiner.benchmarks.JdbcBenchmark
import coroutiner.benchmarks.R2dbcBenchmark
import coroutiner.benchmarks.VertxPgClientBenchmark
import coroutiner.setup.BenchmarkConfig
import coroutiner.setup.BenchmarkConfig.concurrency
import coroutiner.setup.BenchmarkConfig.iterationTime
import coroutiner.setup.PostgresContainerState
import coroutiner.setup.db.JdbcState
import coroutiner.setup.db.R2dbcState
import coroutiner.setup.db.VertxPgState
import io.vertx.kotlin.coroutines.await
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.toList
import kotlinx.coroutines.reactor.awaitSingle
import kotlin.system.measureNanoTime
import kotlin.time.Duration.Companion.nanoseconds



suspend fun <T> measure(name: String, block: suspend () -> T) {
    newFixedThreadPoolContext(concurrency, "benchmark").use { threadPool ->
        val channel = Channel<Long>(Channel.UNLIMITED)
        
        val time = measureNanoTime {
            withTimeoutOrNull(iterationTime) {
                while (isActive) {
                    launch(threadPool) {
                        block.invoke()
                        channel.send(System.nanoTime())
                    }
                }
            }
        }.nanoseconds
        
        channel.close()
        
        val inTimeItems = channel.toList().count()
        
        println("$name made $inTimeItems operations per $iterationTime")
        println("$name took average ${time.div(inTimeItems)} per operation")
    }
}

fun main() {
    runBlocking {
        val postgresContainerState = PostgresContainerState()
        postgresContainerState.open().use { pgContainerState ->
            VertxPgState(pgContainerState).use {
                it.setupBenchmarkData()
            }
            
            launch {
                repeat(BenchmarkConfig.runs) {
                    JdbcState(postgresContainerState)
                        .use { state ->
                            val jdbcBenchmark = JdbcBenchmark()
                            measure("JDBC singleRecordWhere") {
                                jdbcBenchmark.singleRecordWhere(state, BenchmarkConfig.randomUserId())
                            }
                        }
                    
                    
                    VertxPgState(postgresContainerState)
                        .use { state ->
                            val vertxPgClientBenchmark = VertxPgClientBenchmark()
                            measure("VertX singleRecordWhere") {
                                vertxPgClientBenchmark.singleRecordWhere(state, BenchmarkConfig.randomUserId())
                                    .await()
                            }
                        }
                    
                    R2dbcState(postgresContainerState)
                        .use { state ->
                            val r2dbcBenchmark = R2dbcBenchmark()
                            measure("R2DBC singleRecordWhere") {
                                r2dbcBenchmark.singleRecordWhere(state, BenchmarkConfig.randomUserId())
                                    .awaitSingle()
                            }
                        }
                }
            }.join()
        }
    }
}