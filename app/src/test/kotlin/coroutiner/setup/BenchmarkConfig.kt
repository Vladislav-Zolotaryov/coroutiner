package coroutiner.setup

object BenchmarkConfig {
    
    const val poolSize = 10
    
    const val userRecordCount = 80_000
    // Keep it below userRecordCount
    const val recordQueryLimit = 30_000
    
    const val multiWhereSize = 10
    
    const val iterationsPerBenchmark = 40_000
    
    const val runs = 5
    
    fun randomUserId(): Int = (1..userRecordCount).random()
    
}