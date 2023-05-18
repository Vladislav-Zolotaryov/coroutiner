package coroutiner

import coroutiner.benchmarks.JdbcBenchmark
import coroutiner.setup.BenchmarkConfig
import coroutiner.setup.PostgresContainerState
import coroutiner.setup.db.JdbcState
import coroutiner.setup.db.VertxPgState
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
                    JdbcState(postgresContainerState)
                        .use { state ->
                            val jdbcBenchmark = JdbcBenchmark()
                            (1..300000).map { _ ->
                                launch { jdbcBenchmark.singleRecordWhere(state, BenchmarkConfig.randomUserId()) }
                            }.joinAll()
                        }
                }.let { println("JDBC Done in ${it.nanoseconds}") }
            }.join()
        }
    }
}