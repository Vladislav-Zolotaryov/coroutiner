package coroutiner

import coroutiner.setup.*
import io.vertx.kotlin.coroutines.await
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.Tuple
import org.openjdk.jmh.annotations.*
import java.time.LocalDateTime

open class VertxPgClientBenchmark {
    
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    fun singleRecordWhere(postgreState: PostgreState) {
        routine {
            postgreState.pgClient.let { client ->
                val query = client
                    .preparedQuery("${postgreState.baseQuery} WHERE u.id=$1")
                
                postgreState.singleQueryUserIds
                    .map { Tuple.of(it) }
                    .flatMap { tuple ->
                        query.execute(tuple)
                            .await()
                            .map {
                                it.fullUser()
                            }
                    }
                    .let {
                        assert(it.size == postgreState.singleQueryUserIds.size)
                    }
            }
        }
    }
    
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    fun multiRecordWhere(postgreState: PostgreState) {
        routine {
            postgreState.pgClient.let { client ->
                val bindingString =
                    (1..postgreState.singleQueryUserIds.size).joinToString(separator = ", ") { "$$it" }
                
                client
                    .preparedQuery("${postgreState.baseQuery} WHERE u.id IN ($bindingString)")
                    .execute(Tuple.tuple(postgreState.singleQueryUserIds))
                    .await()
                    .map {
                        it.fullUser()
                    }
                    .let {
                        assert(it.size == postgreState.singleQueryUserIds.size)
                    }
            }
        }
    }
    
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    fun largeQueryWithLimit(postgreState: PostgreState) {
        routine {
            postgreState.pgClient.let { client ->
                client
                    .query("${postgreState.baseQuery} LIMIT ${BenchmarkConfig.recordQueryLimit}")
                    .execute()
                    .await()
                    .map {
                        it.fullUser()
                    }
                    .let {
                        assert(it.size == BenchmarkConfig.recordQueryLimit)
                    }
            }
        }
    }
    
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    fun fullScan(postgreState: PostgreState) {
        routine {
            postgreState.pgClient.let { client ->
                client
                    .query(postgreState.baseQuery)
                    .execute()
                    .await()
                    .map {
                        it.fullUser()
                    }
                    .let {
                        assert(it.size == BenchmarkConfig.userRecordCount)
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
            with(VertxPgClientBenchmark()) {
                singleRecordWhere(state)
                multiRecordWhere(state)
                largeQueryWithLimit(state)
                fullScan(state)
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