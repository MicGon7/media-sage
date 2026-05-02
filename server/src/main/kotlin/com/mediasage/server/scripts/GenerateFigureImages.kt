package com.mediasage.server.scripts

import com.mediasage.server.db.FigureRow
import com.mediasage.server.db.ServerDatabase
import com.mediasage.server.service.ImageGenerationException
import com.mediasage.server.service.ImageGenerationService
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.File

private const val RATE_LIMIT_PAUSE_MS = 65_000L
private const val PRICE_LOW = 0.006
private const val PRICE_MEDIUM = 0.053
private const val PRICE_HIGH = 0.211

fun main(args: Array<String>) {
    val batchSize = args.argInt("--batch-size", default = 5)
    val quality = args.argString("--quality", default = "low")
    val startFrom = args.argLong("--start-from", default = 0L)
    val limit = args.argInt("--limit", default = Int.MAX_VALUE)
    val force = "--force" in args
    val dryRun = "--dry-run" in args
    val promptDetail = args.argString("--prompt-detail", default = "")
    val dbPath = System.getenv("DB_PATH") ?: error("DB_PATH env var is not set.")
    val apiKey = System.getenv("OPENAI_API_KEY") ?: error("OPENAI_API_KEY env var is not set.")
    val outputDir = File("generated-images").also { it.mkdirs() }
    val httpClient = buildHttpClient()
    ServerDatabase.init(dbPath)
    val figures = ServerDatabase.fetchAllFigures()
        .filter { it.id >= startFrom }
        .filter { force || it.portraitUrl == null }
        .take(limit)
    printSummary(figures, quality, batchSize, limit, outputDir, dryRun)
    if (dryRun) { runDryRun(figures); httpClient.close(); return }
    runBatchGeneration(figures, batchSize, quality, promptDetail, outputDir, httpClient, apiKey)
    httpClient.close()
}

private fun printSummary(
    figures: List<FigureRow>,
    quality: String,
    batchSize: Int,
    limit: Int,
    outputDir: File,
    dryRun: Boolean
) {
    val price = pricePerImage(quality)
    println("=== Generate Figure Portraits ===")
    println("Figures to process : ${figures.size}")
    if (limit != Int.MAX_VALUE) println("Limit              : $limit")
    println("Quality            : $quality")
    println("Batch size         : $batchSize")
    println("Estimated cost     : \$${"%.3f".format(figures.size * price)}")
    println("Output dir         : ${outputDir.absolutePath}")
    if (dryRun) println("\n--- DRY RUN — no API calls will be made ---")
}

private fun runDryRun(figures: List<FigureRow>) {
    figures.take(20).forEach { figure -> println("  [${figure.id}] ${figure.name}") }
    if (figures.size > 20) println("  ... and ${figures.size - 20} more")
}

private fun runBatchGeneration(
    figures: List<FigureRow>,
    batchSize: Int,
    quality: String,
    promptDetail: String,
    outputDir: File,
    httpClient: HttpClient,
    apiKey: String
) = runBlocking {
    val imageService = ImageGenerationService(httpClient, apiKey)
    var totalCost = 0.0
    var successCount = 0
    figures.chunked(batchSize).forEachIndexed { index, batch ->
        if (index > 0) { println("  Pausing ${RATE_LIMIT_PAUSE_MS / 1000}s for rate limit..."); delay(RATE_LIMIT_PAUSE_MS) }
        println("Batch ${index + 1} (figures ${batch.first().id}–${batch.last().id})")
        batch.forEach { figure ->
            val (ok, cost) = generateOne(figure, quality, promptDetail, outputDir, imageService)
            if (ok) { successCount++; totalCost += cost }
        }
    }
    println("\n=== Complete ===\nGenerated : $successCount / ${figures.size}\nTotal cost: \$${"%.3f".format(totalCost)}")
}

private suspend fun generateOne(
    figure: FigureRow,
    quality: String,
    promptDetail: String,
    outputDir: File,
    imageService: ImageGenerationService
): Pair<Boolean, Double> {
    print("  [${figure.id}] ${figure.name} ... ")
    repeat(2) { attempt ->
        try {
            print("(text only) ")
            val imageBytes = imageService.generateTextOnly(figure.name, figure.role, figure.century, figure.lifespan, quality, promptDetail)
            val file = File(outputDir, "${figure.id}.webp")
            file.writeBytes(imageBytes)
            ServerDatabase.updateFigurePortraitUrl(figure.id, "/images/figures/${figure.id}.webp")
            println("✓  (${file.length() / 1024}KB)")
            return true to pricePerImage(quality)
        } catch (e: ImageGenerationException) {
            if (e.statusCode in listOf(403, 502) && attempt == 0) {
                println("(${e.statusCode} — retrying in 30s) ")
                delay(30_000)
            } else {
                println("✗  ERROR: ${e.message}")
                return false to 0.0
            }
        } catch (e: Exception) {
            println("✗  ERROR: ${e.message}")
            return false to 0.0
        }
    }
    println("✗  ERROR: failed after retry")
    return false to 0.0
}

private fun pricePerImage(quality: String) = when (quality) {
    "medium" -> PRICE_MEDIUM
    "high" -> PRICE_HIGH
    else -> PRICE_LOW
}

private fun buildHttpClient() = HttpClient(OkHttp) {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    install(HttpTimeout) { requestTimeoutMillis = 120_000; connectTimeoutMillis = 10_000; socketTimeoutMillis = 120_000 }
}

private fun Array<String>.argString(key: String, default: String) =
    firstOrNull { it.startsWith("$key=") }?.substringAfter("=") ?: default

private fun Array<String>.argInt(key: String, default: Int) =
    argString(key, default.toString()).toIntOrNull() ?: default

private fun Array<String>.argLong(key: String, default: Long) =
    argString(key, default.toString()).toLongOrNull() ?: default
