package coroutiner

import coroutiner.setup.PostgreState
import coroutiner.setup.asPgClient
import io.vertx.kotlin.coroutines.await
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.Tuple
import org.openjdk.jmh.annotations.*
import java.time.LocalDateTime

open class VertxPgClientBenchmark {
    
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    fun singleRecordRetrieval(postgreState: PostgreState) {
        val postgreSQLContainer = postgreState.postgreSQLContainer
        routine {
            postgreSQLContainer.asPgClient().use { client ->
                val query = client
                    .preparedQuery("${postgreState.baseQuery} WHERE u.id=$1")
                
                postgreState.singleQueryUserIds
                    .map { Tuple.of(it) }
                    .forEach { tuple ->
                        query.execute(tuple)
                            .await()
                            .map {
                                it.fullUser()
                            }
                    }
            }
        }
    }
    
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    fun multiRecordRetrieval(postgreState: PostgreState) {
        val postgreSQLContainer = postgreState.postgreSQLContainer
        routine {
            postgreSQLContainer.asPgClient().use { client ->
                val bindingString =
                    (1..postgreState.singleQueryUserIds.size).map { "$$it" }.joinToString(separator = ", ")
                
                val query = client
                    .preparedQuery("${postgreState.baseQuery} WHERE u.id IN ($bindingString)")
                
                query.execute(Tuple.tuple(postgreState.singleQueryUserIds))
                    .await()
                    .map {
                        it.fullUser()
                    }
            }
        }
    }
    
    
}


// Dev Runner
fun main() {
    PostgreState()
        .open()
        .use { state ->
            VertxPgClientBenchmark().singleRecordRetrieval(state)
            VertxPgClientBenchmark().multiRecordRetrieval(state)
        }
}

data class UserFull(
    val id: Long,
    val name: String,
    val groupId: Long,
    val groupName: String,
    val created: LocalDateTime,
    val updated: LocalDateTime
)

data class UserRaw(val id: Long, val name: String, val groupId: Long)

fun Row.rawUser() = UserRaw(
    id = this.getLong("id"),
    name = this.getString("name"),
    groupId = this.getLong("group_id"),
)

fun Row.fullUser() = UserFull(
    id = this.getLong("user_id"),
    name = this.getString("user_name"),
    groupId = this.getLong("group_id"),
    groupName = this.getString("group_name"),
    created = this.get(LocalDateTime::class.java, "user_created"),
    updated = this.get(LocalDateTime::class.java, "user_updated"),
)