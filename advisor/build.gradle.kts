plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    // shadow is already on the classpath via :orchestrator's Ktor plugin; apply without a version
    id("com.gradleup.shadow")
    application
}

application {
    mainClass.set("com.mediasage.advisor.AdvisorServerKt")
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveBaseName.set("advisor")
    mergeServiceFiles()
}

dependencies {
    // MCP Kotlin SDK
    implementation(libs.mcp.kotlin.sdk)

    // Ktor Client (calls Claude API)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)

    // Exposed + PostgreSQL (reads from Supabase jobs/transcripts tables)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)
    implementation(libs.postgresql.jdbc)

    // Serialization + logging
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.logback)

    // Pipeline schema (JobsTable, TranscriptsTable, DecisionScoresTable)
    implementation(projects.pipelineCore)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}
