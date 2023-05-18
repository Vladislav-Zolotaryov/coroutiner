package coroutiner

import coroutiner.setup.BenchmarkConfig
import coroutiner.setup.PostgreState
import coroutiner.setup.UserFull
import org.openjdk.jmh.annotations.*
import java.sql.ResultSet
import java.time.LocalDateTime
import kotlin.system.measureNanoTime
import kotlin.time.Duration.Companion.nanoseconds

open class JdbcBenchmark {
    
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    fun singleRecordWhere(postgreState: PostgreState) {
        with(postgreState.hikariPool) {
            connection.use { cn ->
                cn.prepareStatement("${postgreState.baseQuery} WHERE u.id = ?").use { st ->
                    postgreState.singleQueryUserIds.forEach { id ->
                        st.setInt(1, id)
                        
                        st.executeQuery().use { rs ->
                            val result = mutableListOf<UserFull>()
                            while (rs.next()) {
                                result.add(rs.fullUser())
                            }
                            
                            require(result.size == 1)
                        }
                    }
                }
            }
        }
    }
    
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    fun multiRecordWhere(postgreState: PostgreState) {
        with(postgreState.hikariPool) {
            connection.use { cn ->
                val paramsPlaceholders =
                    (1..postgreState.singleQueryUserIds.size).map { "?" }.joinToString(separator = ", ") { "?" }
                
                cn.prepareStatement("${postgreState.baseQuery} WHERE u.id IN ($paramsPlaceholders)").use { st ->
                    postgreState.singleQueryUserIds.withIndex().forEach { (index, id) ->
                        st.setObject(index + 1, id)
                    }
                    
                    st.executeQuery().use { rs ->
                        val result = mutableListOf<UserFull>()
                        while (rs.next()) {
                            result.add(rs.fullUser())
                        }
                        
                        require(result.size == postgreState.singleQueryUserIds.size)
                    }
                }
            }
        }
    }
    
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    fun largeQueryWithLimit(postgreState: PostgreState) {
        with(postgreState.hikariPool) {
            connection.use { cn ->
                cn.prepareStatement("${postgreState.baseQuery} LIMIT ?").use { st ->
                    st.setInt(1, BenchmarkConfig.recordQueryLimit)
                    
                    st.executeQuery().use { rs ->
                        val result = mutableListOf<UserFull>()
                        while (rs.next()) {
                            result.add(rs.fullUser())
                        }
                        
                        require(result.size == BenchmarkConfig.recordQueryLimit)
                    }
                }
            }
        }
    }
    
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    fun fullScan(postgreState: PostgreState) {
        with(postgreState.hikariPool) {
            connection.use { cn ->
                cn.createStatement().use { st ->
                    st.executeQuery(postgreState.baseQuery).use { rs ->
                        val result = mutableListOf<UserFull>()
                        while (rs.next()) {
                            result.add(rs.fullUser())
                        }
                        
                        require(result.size == BenchmarkConfig.userRecordCount)
                    }
                }
            }
        }
    }
}

fun jdbcRunPre(state: PostgreState) {
    with(JdbcBenchmark()) {
        singleRecordWhere(state)
        measureNanoTime {
            singleRecordWhere(state)
        }.let { println("JDBC singleRecordWhere took ${it.nanoseconds / 5} per operation") }
        
        multiRecordWhere(state)
        measureNanoTime {
            multiRecordWhere(state)
        }.let { println("JDBC multiRecordWhere took ${it.nanoseconds / 5} per operation") }
        
        largeQueryWithLimit(state)
        measureNanoTime {
            largeQueryWithLimit(state)
        }.let { println("JDBC largeQueryWithLimit took ${it.nanoseconds / 5} per operation") }
        
        fullScan(state)
        measureNanoTime {
            fullScan(state)
        }.let { println("JDBC fullScan took ${it.nanoseconds / 5} per operation") }
    }
}

fun jdbcRun() {
    PostgreState()
        .open()
        .use { state ->
            jdbcRunPre(state)
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
