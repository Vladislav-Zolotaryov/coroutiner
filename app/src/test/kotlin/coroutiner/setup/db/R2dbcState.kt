package coroutiner.setup.db

import coroutiner.setup.BenchmarkConfig
import coroutiner.setup.PostgresContainerState
import io.r2dbc.pool.ConnectionPool
import io.r2dbc.pool.ConnectionPoolConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import io.r2dbc.spi.Connection
import org.testcontainers.containers.PostgreSQLContainer
import reactor.core.publisher.Mono
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

class R2dbcState(postgresContainerState: PostgresContainerState) : AutoCloseable {
    
    private val r2dbcPool: ConnectionPool = postgresContainerState.postgreSQLContainer.asR2dbcPool()
    
    init {
        r2dbcPool.warmup()
    }
    
    fun <T> connection(block: (Connection) -> Mono<T>): Mono<T> {
        return Mono.usingWhen(
            r2dbcPool.create(),
            { r2dbcConn -> block.invoke(r2dbcConn) },
            Connection::close,
        )
    }
    
    override fun close() {
        r2dbcPool.dispose()
    }
}


private fun PostgreSQLContainer<*>.asR2dbcPool(): ConnectionPool {
    val configuration = PostgresqlConnectionConfiguration.builder()
        .host(host)
        .port(firstMappedPort)
        .database(databaseName)
        .username(username)
        .password(password)
        .build()
    
    
    val poolConfiguration = ConnectionPoolConfiguration.builder(PostgresqlConnectionFactory(configuration))
        .maxSize(BenchmarkConfig.poolSize)
        .maxIdleTime(1000.milliseconds.toJavaDuration())
        .build()
    
    return ConnectionPool(poolConfiguration)
}

