package coroutiner.benchmarks

import coroutiner.setup.BenchmarkConfig
import coroutiner.setup.BenchmarkQueries
import coroutiner.setup.UserFull
import coroutiner.setup.db.R2dbcState
import io.r2dbc.spi.Row
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.LocalDateTime

open class R2dbcBenchmark {
    
    fun singleRecordWhere(r2dbcState: R2dbcState, userId: Int): Mono<UserFull> {
        return r2dbcState.connection { connection ->
            connection.createStatement("${BenchmarkQueries.baseQuery} WHERE u.id = $1")
                .bind("$1", userId)
                .execute()
                .toFlux()
                .flatMap { result ->
                    result.map { row, _ ->
                        row.fullUser()
                    }
                }
                .collectList()
                .map {
                    it.first()
                }
        }
    }
    
    fun multiRecordWhere(r2dbcState: R2dbcState, targetIds: List<Int>): Mono<List<UserFull>> {
        return r2dbcState.connection { connection ->
            val paramsPlaceholders =
                (1..targetIds.size).joinToString(separator = ", ") { "$$it" }
            
            val statement =
                connection.createStatement("${BenchmarkQueries.baseQuery} WHERE u.id IN ($paramsPlaceholders)")
            
            targetIds.withIndex().forEach { (index, id) ->
                statement.bind("$${index + 1}", id)
            }
            
            statement.execute()
                .toFlux()
                .flatMap { result ->
                    result.map { row, _ ->
                        row.fullUser()
                    }
                }.collectList()
        }
    }
    
    fun largeQueryWithLimit(r2dbcState: R2dbcState): Mono<List<UserFull>> {
        return r2dbcState.connection { connection ->
            connection.createStatement("${BenchmarkQueries.baseQuery} LIMIT $1")
                .bind("$1", BenchmarkConfig.recordQueryLimit)
                .execute()
                .toFlux()
                .flatMap { result ->
                    result.map { row, _ ->
                        row.fullUser()
                    }
                }.collectList()
        }
    }
    
    fun fullScan(r2dbcState: R2dbcState): Mono<List<UserFull>> {
        return r2dbcState.connection { connection ->
            connection.createStatement(BenchmarkQueries.baseQuery)
                .execute()
                .toFlux()
                .flatMap { result ->
                    result.map { row, _ ->
                        row.fullUser()
                    }
                }.collectList()
        }
    }
}

fun Row.fullUser() = UserFull(
    id = get("user_id") as Long,
    name = get("user_name") as String,
    groupId = get("group_id") as Long,
    groupName = get("group_name") as String,
    created = get("user_created") as LocalDateTime,
    updated = get("user_updated") as LocalDateTime,
)

fun <T> Publisher<T>.toFlux(): Flux<T> = Flux.from(this)
