package coroutiner

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import io.vertx.kotlin.coroutines.await
import io.vertx.pgclient.PgConnectOptions
import io.vertx.pgclient.PgPool
import io.vertx.sqlclient.PoolOptions
import io.vertx.sqlclient.SqlClient
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.reactive.awaitSingle
import kotlin.random.Random


data class Stuff(val value: String)

suspend fun getStuffFromDb(): Stuff {
    delay(500)
    println("Done waiting for DB")
    return Stuff("Here you go")
}

data class Some(val value: String)

suspend fun getSomeFromOtherService(): Some {
    delay(1000)
    println("Done waiting for service")
    throw IllegalStateException("Bad")
}

suspend fun SqlClient.use(block: suspend (SqlClient) -> Unit) {
    try {
        block.invoke(this)
    } finally {
        this.close()
    }
}

fun main() {
    val connectOptions = PgConnectOptions()
        .setPort(5432)
        .setHost("127.0.0.1")
        .setDatabase("app")
        .setUser("myuser")
        .setPassword("mypass")
    
    val poolOptions = PoolOptions()
        .setMaxSize(5)
    
    
    runBlocking {
        PgPool.client(connectOptions, poolOptions).use { client ->
            client.query("CREATE TABLE IF NOT EXISTS users (id bigserial primary key, name text)").execute().await()
            client.query("INSERT INTO users (name) VALUES ('test')").execute().await()
            
            val result = client
                .query("SELECT * FROM users")
                .execute()
                .await()
            
            println(result.joinToString(separator = ", ") { it.getLong("id").toString() })
        }
    }
    
    val configuration = PostgresqlConnectionConfiguration.builder()
        .host("0.0.0.0")
        .port(5432)
        .database("app")
        .username("myuser")
        .password("mypass")
        .build()
    
    val connectionFactory = PostgresqlConnectionFactory(configuration)
    
    runBlocking {
        val result = connectionFactory
            .create()
            .flatMapMany { connection ->
                connection.createStatement("SELECT * FROM users")
                    .execute()
                    .flatMap { result -> result.map { row, _ -> row.get("id") as Long } }
            }.collectList().awaitSingle()
        
        println(result.joinToString(separator = ", "))
    }
    
    runBlocking {
        val dbStuff = async { getStuffFromDb() }
        supervisorScope {
            val serviceStuff = async {
                getSomeFromOtherService()
            }
            println(dbStuff.await().value + runCatching { serviceStuff.await() }.getOrDefault(""))
        }
    }
    
    runBlocking {
        println(getStuffFromDb().value + runCatching { getSomeFromOtherService().value }.getOrDefault(""))
    }
    
    runBlocking {
        val channel = Channel<Int>()
        launch {
            (0..5).forEach { x -> channel.send(x * x) }
            channel.close()
        }
        
        (0..5).map { async { val value = channel.receive(); println(value) } }.awaitAll()
        println("Done!")
    }
    
    runBlocking {
        flow {
            while (true) {
                delay(100)
                if (Random.nextInt(0, 100) <= 20) {
                    throw IllegalStateException("Boo")
                }
                emit("test")
            }
        }
            .catch { emit("fail") }
            .onEach { println(it) }
            .onCompletion { println("Done too") }
            .collect()
    }
}

