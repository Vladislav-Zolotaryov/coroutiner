package coroutiner.setup

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.pool.HikariPool
import coroutiner.setup.BenchmarkConfig.poolSize
import coroutiner.setup.BenchmarkConfig.userRecordCount
import io.r2dbc.pool.ConnectionPool
import io.r2dbc.pool.ConnectionPoolConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import io.vertx.core.impl.cpu.CpuCoreSensor
import io.vertx.kotlin.coroutines.await
import io.vertx.pgclient.PgConnectOptions
import io.vertx.pgclient.PgPool
import io.vertx.sqlclient.PoolOptions
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.SqlClient
import io.vertx.sqlclient.Tuple
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.openjdk.jmh.annotations.*
import org.slf4j.LoggerFactory
import org.testcontainers.containers.PostgreSQLContainer
import kotlin.system.measureNanoTime
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds


@State(Scope.Benchmark)
class PostgreState : AutoCloseable {
    
    val log = LoggerFactory.getLogger(PostgreState::class.java)
    
    val postgreSQLContainer = PostgreSQLContainer("postgres:15-alpine")
    
    var singleQueryUserIds =
        listOf(
            userRecordCount * 0.1,
            userRecordCount * 0.2,
            userRecordCount * 0.5,
            userRecordCount * 0.9
        ).map { it.toInt() }
    
    val baseQuery =
        "SELECT u.id as user_id, u.name as user_name, g.id as group_id, g.name as group_name, u.created as user_created, u.updated as user_updated FROM users u JOIN groups g ON g.id = u.group_id"
    
    lateinit var pgClient: SqlClient
    
    lateinit var connectionPool: ConnectionPool
    
    lateinit var hikariPool: HikariPool
    
    @Setup
    fun setup() {
        postgreSQLContainer.start()
        
        pgClient = postgreSQLContainer.asPgClient()
        connectionPool = postgreSQLContainer.asR2dbcFactory()
        
        val hikariConfig = HikariConfig().apply {
            maximumPoolSize = poolSize
            jdbcUrl = "jdbc:postgresql://${postgreSQLContainer.host}:${postgreSQLContainer.firstMappedPort}/${postgreSQLContainer.databaseName}"
            username = postgreSQLContainer.username
            password = postgreSQLContainer.password
        }
        hikariPool = HikariPool(hikariConfig)
        
        runBlocking {
            postgreSQLContainer
            pgClient
                .let { client ->
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
                    
                    val createUserQuery = client.preparedQuery("INSERT INTO users (name, group_id) VALUES ($1, $2)")
                    
                    measureNanoTime {
                        (1..userRecordCount)
                            .map {
                                Tuple.of(
                                    names.random(),
                                    groupIdDistribution(it, userRecordCount, groups)
                                )
                            }
                            .map { userTuple ->
                                async {
                                    createUserQuery.execute(userTuple)
                                }
                            }.awaitAll()
                    }.let {
                        log.info("Creating users took ${it.nanoseconds}")
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
        pgClient.close()
        connectionPool.close()
        postgreSQLContainer.stop()
        hikariPool.shutdown()
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

private fun PostgreSQLContainer<*>.asR2dbcFactory(): ConnectionPool {
    val configuration = PostgresqlConnectionConfiguration.builder()
        .host(host)
        .port(firstMappedPort)
        .database(databaseName)
        .username(username)
        .password(password)
        .build()
    
    
    val poolConfiguration = ConnectionPoolConfiguration.builder(PostgresqlConnectionFactory(configuration))
        .maxSize(poolSize)
        .build()
    
    return ConnectionPool(poolConfiguration)
}

private fun PostgreSQLContainer<*>.asPgClient(): SqlClient {
    val connectOptions = PgConnectOptions()
        .setPort(firstMappedPort)
        .setHost(host)
        .setUser(username)
        .setPassword(password)
        .setDatabase(databaseName)
    
    return PgPool.client(
        connectOptions,
        PoolOptions()
            .setMaxSize(poolSize)
            .setEventLoopSize(12)
    )
}

fun Row.rawUser() = UserRaw(
    id = this.getLong("id"),
    name = this.getString("name"),
    groupId = this.getLong("group_id"),
)