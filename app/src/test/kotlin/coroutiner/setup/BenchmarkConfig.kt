package coroutiner.setup

import kotlin.time.Duration.Companion.seconds

object BenchmarkConfig {
    
    const val poolSize = 10
    
    const val userRecordCount = 80_000
    
    // Keep it below userRecordCount
    const val recordQueryLimit = 30_000
    
    const val multiWhereSize = 10
    
    const val runs = 3
    
    val iterationTime = 2.seconds
    const val concurrency = 100
    
    fun randomUserId(): Int = (1..userRecordCount).random()
    
}