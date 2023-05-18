package coroutiner

import coroutiner.benchmarks.VertxPgClientBenchmark
import coroutiner.setup.BenchmarkConfig
import coroutiner.setup.PostgresContainerState
import coroutiner.setup.db.VertxPgState
import io.vertx.kotlin.coroutines.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
                measureNanoTime {
                    VertxPgState(postgresContainerState)
                        .use { state ->
                            val vertxPgClientBenchmark = VertxPgClientBenchmark()
                            (1..300000).map { _ ->
                                launch {
                                    vertxPgClientBenchmark.singleRecordWhere(state, BenchmarkConfig.randomUserId())
                                        .await()
                                }
                            }.joinAll()
                        }
                }.let { println("VertX Done in ${it.nanoseconds}") }
            }.join()
        }
    }
}