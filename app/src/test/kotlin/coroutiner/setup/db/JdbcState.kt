package coroutiner.setup.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.pool.HikariPool
import coroutiner.setup.BenchmarkConfig
import coroutiner.setup.PostgresContainerState
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection

class JdbcState(postgresContainerState: PostgresContainerState) : AutoCloseable {
    
    private val hikariPool: HikariPool = postgresContainerState.postgreSQLContainer.asHikariCp()
    
    fun <T> connection(block: (Connection) -> T): T {
        return hikariPool.connection.use {
            block.invoke(it)
        }
    }
    
    override fun close() {
        hikariPool.shutdown()
    }
}

private fun PostgreSQLContainer<*>.asHikariCp(): HikariPool {
    val postgreSQLContainer = this
    val hikariConfig = HikariConfig().apply {
        maximumPoolSize = BenchmarkConfig.poolSize
        jdbcUrl =
            "jdbc:postgresql://${postgreSQLContainer.host}:${postgreSQLContainer.firstMappedPort}/${postgreSQLContainer.databaseName}"
        username = postgreSQLContainer.username
        password = postgreSQLContainer.password
    }
    return HikariPool(hikariConfig)
}