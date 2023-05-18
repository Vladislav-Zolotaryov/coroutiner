package coroutiner

import coroutiner.benchmarks.JdbcBenchmark
import coroutiner.benchmarks.R2dbcBenchmark
import coroutiner.benchmarks.VertxPgClientBenchmark
import coroutiner.setup.BenchmarkConfig
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


suspend fun <T> measure(
    name: String,
    block: suspend () -> T
) {
    measureNanoTime {
        val channel = Channel<Long>(1_000_000)
        
        val time = measureNanoTime {
            try {
                withTimeout(iterationTime) {
                    while (isActive) {
                        launch(Dispatchers.IO) {
                            block.invoke()
                            channel.send(System.nanoTime())
                        }
                    }
                }
            } catch (e: Exception) {
                if (e !is TimeoutCancellationException) {
                    println(e)
                }
            }
        }.nanoseconds
        
        channel.close()
        
        val inTimeItems = channel.toList().count()
        
        println("$name made $inTimeItems operations per $iterationTime")
        println("$name took average ${time.div(inTimeItems)} per operation")
    }.let {
        println("$name measurement took ${it.nanoseconds}")
    }
}

@OptIn(DelicateCoroutinesApi::class)
fun main() {
    runBlocking {
        val postgresContainerState = PostgresContainerState()
        postgresContainerState.open().use { pgContainerState ->
            VertxPgState(pgContainerState).use {
                it.setupBenchmarkData()
            }
            
            repeat(BenchmarkConfig.runs) {
                val whereIds = (1..BenchmarkConfig.multiWhereSize).map {
                    (1..BenchmarkConfig.userRecordCount).random()
                }
                
                JdbcState(postgresContainerState)
                    .use { state ->
                        val jdbcBenchmark = JdbcBenchmark()
                        measure("JDBC singleRecordWhere") {
                            jdbcBenchmark.singleRecordWhere(state, BenchmarkConfig.randomUserId())
                        }
                        measure("JDBC multiRecordWhere") {
                            jdbcBenchmark.multiRecordWhere(state, whereIds)
                        }
                        measure("JDBC largeQueryWithLimit") {
                            jdbcBenchmark.largeQueryWithLimit(state)
                        }
                    }
                
                
                VertxPgState(postgresContainerState)
                    .use { state ->
                        val vertxPgClientBenchmark = VertxPgClientBenchmark()
                        measure("VertX singleRecordWhere") {
                            vertxPgClientBenchmark.singleRecordWhere(state, BenchmarkConfig.randomUserId())
                                .await()
                        }
                        measure("VertX multiRecordWhere") {
                            vertxPgClientBenchmark.multiRecordWhere(state, whereIds).await()
                        }
                        measure("VertX largeQueryWithLimit") {
                            vertxPgClientBenchmark.largeQueryWithLimit(state).await()
                        }
                    }
                
                R2dbcState(postgresContainerState)
                    .use { state ->
                        val r2dbcBenchmark = R2dbcBenchmark()
                        measure("R2DBC singleRecordWhere") {
                            r2dbcBenchmark.singleRecordWhere(state, BenchmarkConfig.randomUserId())
                                .awaitSingle()
                        }
                        measure("R2DBC multiRecordWhere") {
                            r2dbcBenchmark.multiRecordWhere(state, whereIds).awaitSingle()
                        }
                        measure("R2DBC largeQueryWithLimit") {
                            r2dbcBenchmark.largeQueryWithLimit(state).awaitSingle()
                        }
                    }
            }
        }
    }
}