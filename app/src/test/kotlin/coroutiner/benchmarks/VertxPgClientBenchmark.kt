package coroutiner.benchmarks

import coroutiner.setup.BenchmarkConfig
import coroutiner.setup.BenchmarkQueries
import coroutiner.setup.UserFull
import coroutiner.setup.db.VertxPgState
import io.vertx.core.Future
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.Tuple
import java.time.LocalDateTime

open class VertxPgClientBenchmark {
    
    fun singleRecordWhere(vertxPgState: VertxPgState, userId: Int): Future<UserFull> {
        return vertxPgState.connection { client ->
            val query = client
                .preparedQuery("${BenchmarkQueries.baseQuery} WHERE u.id=$1")
            
            query.execute(Tuple.of(userId))
                .map {
                    it.map { row ->
                        row.fullUser()
                    }.first()
                }
        }
    }
    
    fun multiRecordWhere(vertxPgState: VertxPgState, targetIds: List<Int>): Future<List<UserFull>> {
        return vertxPgState.connection { client ->
            val bindingString = (1..targetIds.size).joinToString(separator = ", ") { "$$it" }
            
            client
                .preparedQuery("${BenchmarkQueries.baseQuery} WHERE u.id IN ($bindingString)")
                .execute(Tuple.tuple(targetIds))
                .map {
                    it.map { row ->
                        row.fullUser()
                    }
                }
        }
    }
    
    fun largeQueryWithLimit(vertxPgState: VertxPgState): Future<List<UserFull>> {
        return vertxPgState.connection { client ->
            client
                .query("${BenchmarkQueries.baseQuery} LIMIT ${BenchmarkConfig.recordQueryLimit}")
                .execute()
                .map {
                    it.map { row ->
                        row.fullUser()
                    }
                }
        }
    }
}

fun Row.fullUser() = UserFull(
    id = this.getLong("user_id"),
    name = this.getString("user_name"),
    groupId = this.getLong("group_id"),
    groupName = this.getString("group_name"),
    created = this.get(LocalDateTime::class.java, "user_created"),
    updated = this.get(LocalDateTime::class.java, "user_updated"),
)