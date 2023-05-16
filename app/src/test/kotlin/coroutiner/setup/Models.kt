package coroutiner.setup

import java.time.LocalDateTime

data class UserFull(
    val id: Long,
    val name: String,
    val groupId: Long,
    val groupName: String,
    val created: LocalDateTime,
    val updated: LocalDateTime
)

data class UserRaw(val id: Long, val name: String, val groupId: Long)