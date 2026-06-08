package com.mediasage.server.prompts

import com.mediasage.server.repository.QuoteData

object DailyReflectionPrompt {

    data class Params(
        val figureName: String,
        val quotes: List<QuoteData>,
        val headlines: List<String>,
        val tone: String,
        val dayOfWeek: String,
        val previousScriptures: List<String>,
        val previousReflections: List<String>,
        val theme: ReflectionTheme?
    )

    fun buildSystemPrompt(figureName: String) = """
        You are generating a devotional reflection in the voice of $figureName.
        The verified quotes below anchor the theological voice and grounding — draw from your knowledge of these specific source works, letting the quotes shape the direction and register of the reflection.
        Do not invent quotes or attribute specific words to $figureName that are not in the verified set.
        Respond ONLY with valid JSON — no markdown, no explanation outside the JSON.
    """.trimIndent()

    fun buildUserMessage(params: Params): String = buildString {
        appendLine("## Verified Quotes from ${params.figureName}")
        appendLine("Draw from your knowledge of these source works, letting these quotes anchor the theological voice and direction.")
        appendLine()
        params.quotes.forEach { q ->
            appendLine("Source: ${q.source}")
            appendLine("Quote: \"${q.text}\"")
            appendLine()
        }
        if (params.headlines.isNotEmpty()) {
            appendLine("## Today's Headlines (for thematic context only)")
            params.headlines.forEach { appendLine("- $it") }
            appendLine()
        }
        append(buildContextBlock(params.tone, params.dayOfWeek, params.previousScriptures, params.previousReflections, params.theme))
        appendLine("## Instructions")
        appendLine("Write a ${params.tone} devotional reflection in the voice of ${params.figureName} structured in three sections:")
        appendLine("- Insight — what this truth reveals about God, the world, or ourselves (1-3 sentences)")
        appendLine("- Implication — what it asks of us (1-3 sentences)")
        appendLine("- Inspiration — a word of hope or encouragement in ${params.figureName}'s voice (1-3 sentences)")
        appendLine("Maintain ${params.figureName}'s voice throughout. Keep each section brief.")
        appendLine("- Include a scripture reference and the full verse text")
        appendLine("- List the source titles you drew from")
        appendLine()
        appendLine(RESPONSE_FORMAT)
    }

    private fun buildContextBlock(
        tone: String,
        dayOfWeek: String,
        previousScriptures: List<String>,
        previousReflections: List<String>,
        theme: ReflectionTheme?
    ) = buildString {
        val dayContext = if (dayOfWeek.isNotBlank()) "$dayOfWeek, " else ""
        appendLine("## Context")
        appendLine("Today is $dayContext$tone.")
        if (theme != null) {
            appendLine("Focus the scripture selection and reflection on the theme of ${theme.displayName}.")
        }
        if (previousScriptures.isNotEmpty()) {
            appendLine()
            appendLine(PREVIOUS_REFLECTION_INSTRUCTION)
            previousScriptures.zip(previousReflections).forEach { (scripture, reflection) ->
                appendLine("- Scripture: $scripture | Reflection: $reflection")
            }
        }
        appendLine()
    }

    private const val PREVIOUS_REFLECTION_INSTRUCTION =
        "An earlier reflection was shared today. Do NOT reuse the same verse. " +
        "You may revisit a theme if the headlines call for it, but bring a fresh angle, " +
        "a different application, or a deeper dimension — avoid repeating the same argument:"

    private val RESPONSE_FORMAT = """
        Respond ONLY with JSON in this exact format:
        {
          "scriptureReference": "<e.g. Psalm 46:10>",
          "scriptureText": "<full verse text>",
          "insight": "<1-3 sentences — what this truth reveals about God, the world, or ourselves>",
          "implication": "<1-3 sentences — what it asks of us>",
          "inspiration": "<1-3 sentences — a word of hope or encouragement in the figure's voice>",
          "sources": ["<source title>"]
        }
    """.trimIndent()
}
