package com.mediasage.scripts

import com.mediasage.orchestrator.feedback.scoring.ClaudeDecisionScorer
import org.jetbrains.exposed.sql.Database
import com.mediasage.pipeline.core.DecisionScoresTable
import com.mediasage.pipeline.core.TranscriptsTable
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

fun main(args: Array<String>) {
    val dryRun = "--dry-run" in args
    val dbUrl = System.getenv("SUPABASE_DB_URL") ?: error("SUPABASE_DB_URL env var is not set.")
    val authToken = System.getenv("ANTHROPIC_AUTH_TOKEN") ?: error("ANTHROPIC_AUTH_TOKEN env var is not set.")
    val baseUrl = System.getenv("ANTHROPIC_BASE_URL") ?: "https://api.anthropic.com"

    initDatabase(dbUrl)
    val jobIds = findScorableJobs()
    println("=== Backfill Decision Scores ===")
    println("Jobs to score : ${jobIds.size}")

    if (dryRun) { runDryRun(jobIds); return }
    if (jobIds.isEmpty()) { println("Nothing to backfill."); return }

    val httpClient = buildHttpClient()
    val (success, failure) = runScoring(jobIds, ClaudeDecisionScorer(httpClient, authToken, baseUrl))
    httpClient.close()

    println("\n=== Complete ===")
    println("Found   : ${jobIds.size}")
    println("Success : $success")
    println("Failed  : $failure")
}

private fun runDryRun(jobIds: List<UUID>) {
    println("\n--- DRY RUN — no Claude calls will be made ---")
    jobIds.forEach { println("  $it") }
}

private fun runScoring(jobIds: List<UUID>, scorer: ClaudeDecisionScorer): Pair<Int, Int> {
    var successCount = 0
    var failureCount = 0
    runBlocking {
        jobIds.forEach { jobId ->
            runCatching {
                deleteScores(jobId)
                scorer.score(jobId)
            }
                .onSuccess { successCount++ }
                .onFailure { e -> failureCount++; println("ERROR [$jobId]: ${e.message}") }
        }
    }
    return successCount to failureCount
}

private fun initDatabase(postgresUrl: String) {
    val uri = java.net.URI(postgresUrl)
    val (user, password) = uri.userInfo.split(":", limit = 2)
    Database.connect(
        url = "jdbc:postgresql://${uri.host}:${uri.port}${uri.path}?sslmode=require",
        driver = "org.postgresql.Driver",
        user = user,
        password = password,
    )
}

private fun buildHttpClient() = HttpClient(OkHttp) {
    install(HttpTimeout) { requestTimeoutMillis = 60_000; socketTimeoutMillis = 60_000 }
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
}

private fun deleteScores(jobId: UUID) = transaction {
    DecisionScoresTable.deleteWhere { DecisionScoresTable.jobId eq jobId }
}

private fun findScorableJobs(): List<UUID> = transaction {
    val scoredWithV2JobIds = DecisionScoresTable
        .selectAll()
        .where { DecisionScoresTable.recommendation neq "" }
        .map { it[DecisionScoresTable.jobId] }
        .toSet()

    TranscriptsTable
        .selectAll()
        .map { it[TranscriptsTable.jobId] }
        .filter { it !in scoredWithV2JobIds }
}
