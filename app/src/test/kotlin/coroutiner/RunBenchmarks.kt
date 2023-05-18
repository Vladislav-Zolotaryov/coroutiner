package coroutiner

import coroutiner.setup.PostgreState
import kotlinx.coroutines.*
import kotlin.system.measureNanoTime
import kotlin.time.Duration.Companion.nanoseconds


fun main() {
    runBlocking {
        PostgreState()
            .open()
            .use { state ->
                launch(Dispatchers.IO) {
                    repeat(1) {
                        
                        val jdbcBenchmark = JdbcBenchmark()
                        (1..40000).map {
                            async {
                                measureNanoTime {
                                    jdbcBenchmark.singleRecordWhere(state)
                                }.nanoseconds
                            }
                        }.awaitAll()
                            .reduce { left, right -> left.plus(right) }
                            .let {
                                println("JDBC singleRecordWhere took average ${it.div(40000)} per operation")
                            }
                        
                        
                        val vertxPgClientBenchmark = VertxPgClientBenchmark()
                        (1..40000).map {
                            async {
                                measureNanoTime {
                                    vertxPgClientBenchmark.singleRecordWhere(state)
                                }.nanoseconds
                            }
                        }.awaitAll()
                            .map { println(it); it }
                            .reduce { left, right -> left.plus(right) }
                            .let {
                                println("VertX singleRecordWhere took average ${it.div(40000)} per operation")
                            }
                        
                        val r2dbcBenchmark = R2dbcBenchmark()
                        (1..40000).map {
                            async {
                                measureNanoTime {
                                    r2dbcBenchmark.singleRecordWhere(state)
                                }.nanoseconds
                            }
                        }.awaitAll()
                            .reduce { left, right -> left.plus(right) }
                            .let {
                                println("R2DBC singleRecordWhere took average ${it.div(40000)} per operation")
                            }
                        
                        
                        (1..40000).map {
                            async {
                                measureNanoTime {
                                    jdbcBenchmark.multiRecordWhere(state)
                                }.nanoseconds
                            }
                        }.awaitAll()
                            .reduce { left, right -> left.plus(right) }
                            .let {
                                println("JDBC multiRecordWhere took average ${it.div(40000)} per operation")
                            }
                        
                        
                        (1..40000).map {
                            async {
                                measureNanoTime {
                                    vertxPgClientBenchmark.multiRecordWhere(state)
                                }.nanoseconds
                            }
                        }.awaitAll()
                            .reduce { left, right -> left.plus(right) }
                            .let {
                                println("VertX multiRecordWhere took average ${it.div(40000)} per operation")
                            }
                        
                        (1..40000).map {
                            async {
                                measureNanoTime {
                                    r2dbcBenchmark.multiRecordWhere(state)
                                }.nanoseconds
                            }
                        }.awaitAll()
                            .reduce { left, right -> left.plus(right) }
                            .let {
                                println("R2DBC multiRecordWhere took average ${it.div(40000)} per operation")
                            }
                        
                    }
                }.join()
            }
        
        /*(1..20).map {
            async { vertxRun() }
        }.awaitAll()
        
        (1..20).map {
            async { r2dbcRun() }
        }.awaitAll()*/
    }
}