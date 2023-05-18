package coroutiner


import coroutiner.benchmarks.R2dbcBenchmark
import coroutiner.setup.BenchmarkConfig
import coroutiner.setup.PostgresContainerState
import coroutiner.setup.db.R2dbcState
import coroutiner.setup.db.VertxPgState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactor.awaitSingle
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
                    R2dbcState(postgresContainerState)
                        .use { state ->
                            val r2dbcBenchmark = R2dbcBenchmark()
                            (1..300000).map { _ ->
                                launch {
                                    r2dbcBenchmark.singleRecordWhere(state, BenchmarkConfig.randomUserId())
                                        .awaitSingle()
                                }
                            }.joinAll()
                        }
                }.let { println("R2DBC Done in ${it.nanoseconds}") }
            }.join()
        }
    }
}