package com.mediasage.scripts

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Base64
import javax.imageio.ImageIO

// Sizes: density label → (legacy px, adaptive foreground px)
private val ANDROID_DENSITIES = listOf(
    Triple("mipmap-mdpi",    48,  108),
    Triple("mipmap-hdpi",    72,  162),
    Triple("mipmap-xhdpi",   96,  216),
    Triple("mipmap-xxhdpi",  144, 324),
    Triple("mipmap-xxxhdpi", 192, 432),
)

// Safe-zone ratio for adaptive icon foreground: visible area is 72dp out of 108dp total
private const val ADAPTIVE_SAFE_ZONE_RATIO = 72.0 / 108.0

private val json = Json { ignoreUnknownKeys = true }

fun main(args: Array<String>) {
    val quality = args.argString("--quality", "high")
    val sourcePath = args.argString("--source", "")
    val dryRun = "--dry-run" in args

    println("=== Generate App Icon ===")
    if (sourcePath.isNotEmpty()) println("Source  : $sourcePath") else println("Quality : $quality")
    if (dryRun) { println("--- DRY RUN — no files written ---\n"); printTargetPaths(); return }

    val master = loadMasterImage(sourcePath, quality)
    println("Writing assets …")
    writeIosIcon(master)
    writeAndroidAssets(master)
    println("\nDone. Next step: build the app and verify the icon on device/emulator.")
}

private fun loadMasterImage(sourcePath: String, quality: String): BufferedImage {
    if (sourcePath.isNotEmpty()) {
        val file = File(sourcePath)
        require(file.exists()) { "Source file not found: $sourcePath" }
        return ImageIO.read(file) ?: error("Could not decode image: $sourcePath")
    }
    val apiKey = System.getenv("OPENAI_API_KEY") ?: error("OPENAI_API_KEY env var is not set.")
    val client = buildIconHttpClient()
    val imageBytes = runBlocking {
        println("\nCalling gpt-image-2 …")
        val bytes = generateIconPng(client, apiKey, quality)
        client.close()
        bytes
    }
    println("Generated ${imageBytes.size / 1024}KB")
    return ImageIO.read(ByteArrayInputStream(imageBytes)) ?: error("Failed to decode image response as PNG")
}

// ---- iOS ----------------------------------------------------------------

private fun writeIosIcon(master: BufferedImage) {
    val dest = File("iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/app-icon-1024.png")
    writePng(resizeBicubic(master, 1024, 1024), dest)
    println("  iOS   ${dest.path}")
}

// ---- Android ------------------------------------------------------------

private fun writeAndroidAssets(master: BufferedImage) {
    val resDir = File("composeApp/src/androidMain/res")

    // 1. Legacy mipmap PNGs (ic_launcher + ic_launcher_round)
    for ((density, legacyPx, _) in ANDROID_DENSITIES) {
        val scaled = resizeBicubic(master, legacyPx, legacyPx)
        val dir = File(resDir, density)
        writePng(scaled, File(dir, "ic_launcher.png"))
        writePng(scaled, File(dir, "ic_launcher_round.png"))
        println("  Android $density ${legacyPx}×${legacyPx}")
    }

    // 2. Adaptive foreground PNG — padded to leave the safe zone
    //    The foreground canvas is 108dp; the safe zone is 72dp (66.7%).
    //    We render artwork centred in the safe zone and leave the remainder transparent.
    val (_, _, xxxhdpiPx) = ANDROID_DENSITIES.last()
    val artworkPx = (xxxhdpiPx * ADAPTIVE_SAFE_ZONE_RATIO).toInt()
    val foreground = BufferedImage(xxxhdpiPx, xxxhdpiPx, BufferedImage.TYPE_INT_ARGB)
    val g = foreground.createGraphics().also {
        it.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        it.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    }
    val offset = (xxxhdpiPx - artworkPx) / 2
    g.drawImage(resizeBicubic(master, artworkPx, artworkPx), offset, offset, null)
    g.dispose()

    // Remove old vector XML and replace with PNG in drawable/ (same name, PNG wins)
    File(resDir, "drawable-v24/ic_launcher_foreground.xml").delete()
    writePng(foreground, File(resDir, "drawable/ic_launcher_foreground.png"))
    println("  Android drawable/ic_launcher_foreground.png (adaptive foreground, ${xxxhdpiPx}×${xxxhdpiPx})")

    // 3. Solid background color replaces the old vector background
    File(resDir, "drawable/ic_launcher_background.xml").writeText(
        """<?xml version="1.0" encoding="utf-8"?>
<color xmlns:android="http://schemas.android.com/apk/res/android">#1C1A14</color>
"""
    )
    println("  Android drawable/ic_launcher_background.xml (navy background)")
}

// ---- API call -----------------------------------------------------------

private suspend fun generateIconPng(client: HttpClient, apiKey: String, quality: String): ByteArray {
    val response = client.post("https://api.openai.com/v1/images/generations") {
        header(HttpHeaders.Authorization, "Bearer $apiKey")
        contentType(ContentType.Application.Json)
        setBody(IconGenerationRequest(prompt = ICON_PROMPT, quality = quality))
    }
    val body = response.bodyAsText()
    if (response.status.value !in 200..299) error("OpenAI error ${response.status.value}: $body")
    val data = json.decodeFromString<IconApiResponse>(body)
    val b64 = data.data.firstOrNull()?.b64Json ?: error("Empty image response from OpenAI")
    return Base64.getDecoder().decode(b64)
}

// ---- Helpers ------------------------------------------------------------

private fun resizeBicubic(src: BufferedImage, w: Int, h: Int): BufferedImage {
    val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val g = out.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.drawImage(src, 0, 0, w, h, null)
    g.dispose()
    return out
}

private fun writePng(image: BufferedImage, dest: File) {
    dest.parentFile?.mkdirs()
    ImageIO.write(image, "png", dest)
}

private fun printTargetPaths() {
    println("  iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/app-icon-1024.png")
    for ((density, px, _) in ANDROID_DENSITIES) {
        println("  composeApp/src/androidMain/res/$density/ic_launcher.png ($px×$px)")
        println("  composeApp/src/androidMain/res/$density/ic_launcher_round.png ($px×$px)")
    }
    println("  composeApp/src/androidMain/res/drawable/ic_launcher_foreground.png (adaptive foreground)")
    println("  composeApp/src/androidMain/res/drawable/ic_launcher_background.xml (brand color)")
    println("  [DELETE] composeApp/src/androidMain/res/drawable-v24/ic_launcher_foreground.xml")
}

private fun buildIconHttpClient() = HttpClient(OkHttp) {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
    install(HttpTimeout) { requestTimeoutMillis = 300_000; connectTimeoutMillis = 10_000; socketTimeoutMillis = 300_000 }
}

private fun Array<String>.argString(key: String, default: String) =
    firstOrNull { it.startsWith("$key=") }?.substringAfter("=") ?: default

// ---- DTOs ---------------------------------------------------------------

@Serializable
private data class IconGenerationRequest(
    val model: String = "gpt-image-2",
    val prompt: String,
    val n: Int = 1,
    val size: String = "1024x1024",
    val quality: String,
    @SerialName("output_format") val outputFormat: String = "png",
)

@Serializable
private data class IconApiResponse(val data: List<IconData>)

@Serializable
private data class IconData(@SerialName("b64_json") val b64Json: String = "")

// ---- Prompt -------------------------------------------------------------

private val ICON_PROMPT = """
A modern, slightly abstract app icon for a news and faith app.
Square 1024×1024 canvas — the OS clips to a rounded squircle, so keep all content well inside the edges.
Light, airy gradient background — soft warm cream to pale amber (#F5EDD8 to #E8C97A), like morning light.
A single stylized hand reaching upward from the bottom center, fingers open, grasping a loosely rolled newspaper or scroll.
The hand and newspaper are rendered in a clean, abstract flat-illustration style — simplified shapes, no photorealism.
Warm amber-gold (#D4A050) and rich brown (#3D2B1A) as the primary colors for the hand and paper.
The composition feels aspirational and hopeful — reaching for truth, for light, for good news.
Bold enough to read at 48×48 pixels. Centered, with breathing room around the edges.
No text, no letters, no borders, no frames.
""".trimIndent()
