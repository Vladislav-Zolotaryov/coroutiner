package coroutiner.setup

import org.testcontainers.containers.PostgreSQLContainer

class PostgresContainerState : AutoCloseable {
    
    val postgreSQLContainer = PostgreSQLContainer("postgres:15-alpine")
    
    fun open(): PostgresContainerState {
        postgreSQLContainer.start()
        return this
    }
    
    override fun close() {
        postgreSQLContainer.stop()
    }
    
}