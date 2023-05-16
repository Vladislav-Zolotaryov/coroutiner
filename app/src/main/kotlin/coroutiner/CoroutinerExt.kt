package coroutiner

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking

fun <T> routine(block: suspend CoroutineScope.() -> T): T = runBlocking(block = block)