plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ktor)
    application
}

application {
    mainClass.set("com.mediasage.server.ApplicationKt")
}

tasks.register<JavaExec>("generateImages") {
    group = "scripts"
    description = "Generate figure portraits using gpt-image-2. Pass args via -PscriptArgs=\"--batch-size=5 --quality=low --dry-run\""
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.mediasage.server.scripts.GenerateFigureImagesKt")
    args = (project.findProperty("scriptArgs") as String? ?: "").split(" ").filter { it.isNotEmpty() }
    environment("DB_PATH", System.getenv("DB_PATH") ?: "")
    environment("OPENAI_API_KEY", System.getenv("OPENAI_API_KEY") ?: "")
}

dependencies {
    // Ktor Server
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.serialization.json)
    implementation(libs.logback)

    // Ktor Client (for calling Claude API, News API, Scripture API)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)

    // Koin
    implementation(libs.koin.core)
    implementation(libs.koin.ktor)

    // HTML scraping
    implementation(libs.jsoup)

    // Exposed + SQLite (server-side persistence)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.sqlite.jdbc)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Testing
    testImplementation(libs.kotlin.test)
    testImplementation(libs.ktor.server.tests)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.koin.test)
}
