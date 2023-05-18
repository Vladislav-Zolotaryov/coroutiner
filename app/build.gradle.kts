
plugins {
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
    mainClass.set("coroutiner.AppKt")
}