package coroutiner.benchmarks

import coroutiner.setup.BenchmarkConfig
import coroutiner.setup.BenchmarkQueries
import coroutiner.setup.UserFull
import coroutiner.setup.db.JdbcState
import java.sql.ResultSet
import java.time.LocalDateTime

open class JdbcBenchmark {
    
    fun singleRecordWhere(jdbcState: JdbcState, userId: Int): UserFull {
        return jdbcState.connection { cn ->
            cn.prepareStatement("${BenchmarkQueries.baseQuery} WHERE u.id = ?").use { st ->
                st.setInt(1, userId)
                
                st.executeQuery().use { rs ->
                    if (rs.next()) {
                        return@connection rs.fullUser()
                    } else {
                        throw IllegalStateException("User with id='$userId' not found")
                    }
                }
            }
        }
    }
    
    fun multiRecordWhere(jdbcState: JdbcState, targetIds: List<Int>): List<UserFull> {
        val result = mutableListOf<UserFull>()
        
        jdbcState.connection { cn ->
            val paramsPlaceholders =
                (1..targetIds.size).map { "?" }.joinToString(separator = ", ") { "?" }
            
            cn.prepareStatement("${BenchmarkQueries.baseQuery} WHERE u.id IN ($paramsPlaceholders)").use { st ->
                targetIds.withIndex().forEach { (index, id) ->
                    st.setObject(index + 1, id)
                }
                
                st.executeQuery().use { rs ->
                    while (rs.next()) {
                        result.add(rs.fullUser())
                    }
                }
            }
        }
        
        return result
    }
    
    fun largeQueryWithLimit(jdbcState: JdbcState): List<UserFull> {
        val result = mutableListOf<UserFull>()
        
        jdbcState.connection { cn ->
            cn.prepareStatement("${BenchmarkQueries.baseQuery} LIMIT ?").use { st ->
                st.setInt(1, BenchmarkConfig.recordQueryLimit)
                
                st.executeQuery().use { rs ->
                    while (rs.next()) {
                        result.add(rs.fullUser())
                    }
                }
            }
        }
        
        return result
    }
}

fun ResultSet.fullUser() = UserFull(
    id = getLong("user_id"),
    name = getString("user_name"),
    groupId = getLong("group_id"),
    groupName = getString("group_name"),
    created = getObject("user_created", LocalDateTime::class.java),
    updated = getObject("user_updated", LocalDateTime::class.java),
)
