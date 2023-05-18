package coroutiner.setup

import kotlin.time.Duration.Companion.seconds

object BenchmarkConfig {
    
    const val poolSize = 10
    
    const val userRecordCount = 100_000
    
    // Keep it below userRecordCount
    const val recordQueryLimit = 1_000
    
    const val multiWhereSize = 20
    
    const val runs = 1
    
    val iterationTime = 5.seconds
    
    fun randomUserId(): Int = (1..userRecordCount).random()
    
}