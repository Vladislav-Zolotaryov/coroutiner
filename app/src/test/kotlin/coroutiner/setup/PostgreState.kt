package coroutiner.setup

import coroutiner.rawUser
import coroutiner.setup.BenchmarkConfig.userRecordCount
import coroutiner.use
import io.vertx.kotlin.coroutines.await
import io.vertx.pgclient.PgConnectOptions
import io.vertx.pgclient.PgPool
import io.vertx.sqlclient.PoolOptions
import io.vertx.sqlclient.SqlClient
import io.vertx.sqlclient.Tuple
import kotlinx.coroutines.runBlocking
import org.openjdk.jmh.annotations.*
import org.slf4j.LoggerFactory
import org.testcontainers.containers.PostgreSQLContainer


@State(Scope.Benchmark)
class PostgreState : AutoCloseable {
    
    val log = LoggerFactory.getLogger(PostgreState::class.java)
    
    val postgreSQLContainer = PostgreSQLContainer("postgres:15-alpine")
    
    var singleQueryUserIds =
        listOf(userRecordCount * 0.1, userRecordCount * 0.2, userRecordCount * 0.5, userRecordCount * 0.9).map { it.toInt() }
    
    val baseQuery =
        "SELECT u.id as user_id, u.name as user_name, g.id as group_id, g.name as group_name, u.created as user_created, u.updated as user_updated FROM users u JOIN groups g ON g.id = u.group_id"
    
    @Setup
    fun setup() {
        postgreSQLContainer.start()
        
        runBlocking {
            postgreSQLContainer
                .asPgClient()
                .use { client ->
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
                        client.preparedQuery("INSERT INTO groups VALUES ($1, $2)")
                            .executeBatch(groupTuples)
                            .await()
                    }
                    
                    (1..userRecordCount)
                        .map {
                            Tuple.of(
                                names.random(),
                                groupIdDistribution(it, userRecordCount, groups)
                            )
                        }
                        .let { userTuples ->
                            client.preparedQuery("INSERT INTO users (name, group_id) VALUES ($1, $2)")
                                .executeBatch(userTuples)
                                .await()
                        }
                    
                    if (ExecutionConfig.printSetupDataSample) {
                        client
                            .query("SELECT id, group_id, name FROM users LIMIT 5")
                            .execute()
                            .await().let { result ->
                                log.info(result.joinToString(separator = ", ") { it.rawUser().toString() })
                            }
                    }
                }
        }
    }
    
    @TearDown
    fun tearDown() {
        postgreSQLContainer.stop()
    }
    
    private fun groupIdDistribution(index: Int, total: Int, groups: List<String>): Int = when {
        index < total * 0.5 -> groups.indexOf("User")
        index < total * 0.8 -> groups.indexOf("Operator")
        else -> groups.indexOf("Admin")
    }
    
    fun open(): PostgreState {
        setup()
        return this
    }
    
    override fun close() {
        tearDown()
    }
}

fun PostgreSQLContainer<*>.asPgClient(): SqlClient {
    val connectOptions = PgConnectOptions()
        .setPort(this.firstMappedPort)
        .setHost(this.host)
        .setUser(this.username)
        .setPassword(this.password)
        .setDatabase(this.databaseName)
    
    return PgPool.client(
        connectOptions,
        PoolOptions().setMaxSize(BenchmarkConfig.poolSize)
    )
}