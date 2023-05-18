package coroutiner.setup.db

import coroutiner.setup.BenchmarkConfig
import coroutiner.setup.PostgresContainerState
import io.vertx.kotlin.coroutines.await
import io.vertx.pgclient.PgConnectOptions
import io.vertx.pgclient.PgPool
import io.vertx.sqlclient.PoolOptions
import io.vertx.sqlclient.SqlClient
import io.vertx.sqlclient.Tuple
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.PostgreSQLContainer
import kotlin.system.measureNanoTime
import kotlin.time.Duration.Companion.nanoseconds

class VertxPgState(postgresContainerState: PostgresContainerState) : AutoCloseable {
    
    private val log: Logger = LoggerFactory.getLogger(VertxPgState::class.java)
    
    private val vertxPgClient: SqlClient = postgresContainerState.postgreSQLContainer.asVertxPgClient()
    
    fun <T> connection(block: (SqlClient) -> T): T {
        return block.invoke(vertxPgClient)
    }
    
    fun setupBenchmarkData() {
        runBlocking {
            vertxPgClient.let { client ->
                    listOf(
                    """
                    CREATE TABLE IF NOT EXISTS groups (
                        id bigserial primary key,
                        name text not null,
                        created timestamp not null default now(),
                        updated timestamp not null default now(),
                        UNIQUE (name)
                    )
                    """.trimIndent(),
                    """
                    CREATE TABLE IF NOT EXISTS users (
                        id bigserial primary key,
                        group_id bigint not null,
                        name text not null,
                        created timestamp not null default now(),
                        updated timestamp not null default now(),
                        CONSTRAINT fk_user_group FOREIGN KEY(group_id) REFERENCES groups(id)
                    )
                    """.trimIndent()
                    ).forEach { client.query(it).execute().await() }
                    
                    val names = listOf("Bruno", "Mark", "Donna", "Zooma", "Gabby", "Tommy")
                    val groups = listOf("User", "Operator", "Admin")
                    
                    groups.withIndex().map { (index, name) ->
                        Tuple.of(index, name)
                    }.let { groupTuples ->
                        client.preparedQuery("INSERT INTO groups VALUES ($1, $2)").executeBatch(groupTuples).await()
                    }
                    
                    val createUserQuery = client.preparedQuery("INSERT INTO users (name, group_id) VALUES ($1, $2)")
                    
                    measureNanoTime {
                        (1..BenchmarkConfig.userRecordCount).map {
                                Tuple.of(
                                    names.random(),
                                    groupIdDistribution(it, BenchmarkConfig.userRecordCount, groups)
                                )
                            }.map { userTuple ->
                                async {
                                    createUserQuery.execute(userTuple).await()
                                }
                            }.awaitAll()
                    }.let {
                        log.info("Creating users took ${it.nanoseconds}")
                    }
                }
        }
    }
    
    private fun groupIdDistribution(index: Int, total: Int, groups: List<String>): Int = when {
        index < total * 0.5 -> groups.indexOf("User")
        index < total * 0.8 -> groups.indexOf("Operator")
        else -> groups.indexOf("Admin")
    }
    
    override fun close() {
        vertxPgClient.close()
    }
}

private fun PostgreSQLContainer<*>.asVertxPgClient(): SqlClient {
    val connectOptions =
        PgConnectOptions()
            .setPort(firstMappedPort)
            .setHost(host)
            .setUser(username)
            .setPassword(password)
            .setDatabase(databaseName)
    
    return PgPool.client(
        connectOptions,
        PoolOptions()
            .setMaxSize(BenchmarkConfig.poolSize)
            .setEventLoopSize(12)
    )
}
