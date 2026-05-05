plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    application
}

application {
    mainClass.set("com.mediasage.scripts.GenerateFigureImagesKt")
}

tasks.register<JavaExec>("generateImages") {
    group = "scripts"
    description = "Generate figure portraits using gpt-image-2. Pass args via -PscriptArgs=\"--batch-size=5 --quality=low --dry-run\""
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.mediasage.scripts.GenerateFigureImagesKt")
    val baseArgs = (project.findProperty("scriptArgs") as String? ?: "").split(" ").filter { it.isNotEmpty() }
    val promptDetail = project.findProperty("promptDetail") as String? ?: ""
    args = if (promptDetail.isNotEmpty()) baseArgs + "--prompt-detail=$promptDetail" else baseArgs
    environment("DB_PATH", System.getenv("DB_PATH") ?: "")
    environment("OPENAI_API_KEY", System.getenv("OPENAI_API_KEY") ?: "")
}

dependencies {
    // Ktor Client (for calling OpenAI image API)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.logback)

    // Exposed + DB drivers (SQLite for local dev, Postgres for production)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.sqlite.jdbc)
    implementation(libs.postgresql.jdbc)

    // Serialization
    implementation(libs.kotlinx.serialization.json)
}
