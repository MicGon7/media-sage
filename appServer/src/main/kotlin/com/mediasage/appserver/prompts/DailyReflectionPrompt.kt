package com.mediasage.appserver.prompts

import com.mediasage.appserver.repository.QuoteData

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
        Draw from your knowledge of $figureName's published works and thought — let the theological register, vocabulary, and convictions of those works shape the reflection.
        Do not invent quotes or attribute specific words to $figureName that you cannot verify from their actual writings.
        Respond ONLY with valid JSON — no markdown, no explanation outside the JSON.
    """.trimIndent()

    fun buildUserMessage(params: Params): String = buildString {
        val sources = params.quotes.map { it.source }.distinct()
        appendLine("## Source Works from ${params.figureName}")
        appendLine("Draw from your knowledge of these works to shape the theological voice and direction of the reflection.")
        appendLine()
        sources.forEach { appendLine("- $it") }
        appendLine()
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
        appendLine("Maintain ${params.figureName}'s voice throughout.")
        appendLine("Each section must be exactly 1-2 sentences. Stop after 2 sentences — do not continue.")
        appendLine("- Include a scripture reference and the full verse text")
        appendLine("- List the source titles you drew from")
        appendLine(buildChallengeInstruction(params.tone))
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

    private fun buildChallengeInstruction(tone: String): String {
        val framing = if (tone.equals("evening", ignoreCase = true)) {
            "retrospective — inviting the reader to look back on their day"
        } else {
            "anticipatory — inviting the reader to look ahead to their day"
        }
        return "- Include a reflection challenge: one open-ended question, 1-2 sentences, " +
            "addressed to the reader in second person, drawn from the insight/implication/inspiration " +
            "above. Make it $framing. " +
            "Phrase the challenge in plain, everyday language — words a middle schooler would understand. " +
            "Avoid theological or academic vocabulary here, even though the rest of the reflection stays " +
            "in the figure's voice. Keep the underlying idea the same; just make the question itself simple and direct."
    }

    private val RESPONSE_FORMAT = """
        Respond ONLY with JSON in this exact format:
        {
          "scriptureReference": "<e.g. Psalm 46:10>",
          "scriptureText": "<full verse text>",
          "insight": "<1-2 sentences max>",
          "implication": "<1-2 sentences max>",
          "inspiration": "<1-2 sentences max>",
          "sources": ["<source title>"],
          "challenge": "<one open-ended question, 1-2 sentences, second person>"
        }
    """.trimIndent()
}
