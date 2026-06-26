plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    application
}

application {
    mainClass.set("com.mediasage.scripts.GenerateFigureImagesKt")
}

tasks.register<JavaExec>("backfillScores") {
    group = "scripts"
    description = "Backfill decision scores for jobs that have a transcript but no existing scores. Pass --dry-run via -PscriptArgs=\"--dry-run\""
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.mediasage.scripts.BackfillDecisionScoresKt")
    args = (project.findProperty("scriptArgs") as String? ?: "").split(" ").filter { it.isNotEmpty() }
    environment("SUPABASE_DB_URL", System.getenv("SUPABASE_DB_URL") ?: "")
    environment("ANTHROPIC_AUTH_TOKEN", System.getenv("ANTHROPIC_AUTH_TOKEN") ?: "")
    environment("ANTHROPIC_BASE_URL", System.getenv("ANTHROPIC_BASE_URL") ?: "")
}

tasks.register<JavaExec>("generateAppIcon") {
    group = "scripts"
    description = "Generate the app icon via gpt-image-2 and write assets for Android and iOS. Pass --dry-run via -PscriptArgs."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.mediasage.scripts.GenerateAppIconKt")
    args = (project.findProperty("scriptArgs") as String? ?: "").split(" ").filter { it.isNotEmpty() }
    environment("OPENAI_API_KEY", System.getenv("OPENAI_API_KEY") ?: "")
    workingDir = rootProject.projectDir
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
    implementation(projects.analyst)
    implementation(projects.pipelineCore)

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
