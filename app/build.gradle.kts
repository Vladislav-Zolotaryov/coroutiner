
plugins {
    id("me.champeau.jmh") version "0.7.1"
    
    val kotlinPluginVersion = "1.8.21"
    id("org.jetbrains.kotlin.jvm") version kotlinPluginVersion
    id("org.jetbrains.kotlin.plugin.allopen") version kotlinPluginVersion
    application
}

allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

repositories {
    mavenCentral()
}

dependencies {
    val coroutinesVersion = "1.7.1"
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:$coroutinesVersion")
    
    val vertxVersion = "4.4.2"
    implementation("io.vertx:vertx-lang-kotlin-coroutines:$vertxVersion")
    implementation("io.vertx:vertx-pg-client:$vertxVersion")
    
    // God damn r2dbc won't resolve hosts without it
    implementation("io.netty:netty-transport-native-epoll:4.1.92.Final")
    
    implementation("org.postgresql:r2dbc-postgresql:1.0.1.RELEASE")
    implementation("io.r2dbc:r2dbc-pool:1.0.0.RELEASE")
    
    // JDBC
    implementation("com.zaxxer:HikariCP:5.0.1")
    implementation("org.postgresql:postgresql:42.6.0")
    
    val scramVersion = "2.1"
    implementation("com.ongres.scram:common:$scramVersion")
    implementation("com.ongres.scram:client:$scramVersion")
    
    implementation("org.slf4j:slf4j-simple:2.0.7")
    
    val jmhVersion = "1.36"
    testImplementation("org.openjdk.jmh:jmh-core:$jmhVersion")
    testImplementation("org.openjdk.jmh:jmh-generator-annprocess:$jmhVersion")
    
    val testContainersVersion = "1.18.1"
    testImplementation("org.testcontainers:testcontainers:$testContainersVersion")
    testImplementation("org.testcontainers:postgresql:$testContainersVersion")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

application {
    // Define the main class for the application.
    mainClass.set("coroutiner.AppKt")
}

jmh {
    warmup.set("2s")
    warmupForks.set(1)
    warmupIterations.set(1)
    warmupBatchSize.set(1)
    
    timeOnIteration.set("2s")
    fork.set(1)
    iterations.set(1)
    threads.set(50)
}