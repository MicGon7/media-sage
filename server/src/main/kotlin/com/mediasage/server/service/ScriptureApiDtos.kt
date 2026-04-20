package com.mediasage.server.service

import kotlinx.serialization.Serializable

// ---- Response DTOs for API.Bible ----

@Serializable
data class ScriptureSearchResponse(
    val query: String = "",
    val data: ScriptureSearchData = ScriptureSearchData()
)

@Serializable
data class ScriptureSearchData(
    val query: String = "",
    val limit: Int = 0,
    val offset: Int = 0,
    val total: Int = 0,
    val verseCount: Int = 0,
    val verses: List<ScriptureVerse> = emptyList()
)

@Serializable
data class ScripturePassageResponse(
    val data: ScripturePassage
)

@Serializable
data class ScripturePassage(
    val id: String,
    val orgId: String = "",
    val bibleId: String = "",
    val reference: String = "",
    val content: String = "",
    val copyright: String = ""
)

@Serializable
data class ScriptureVerse(
    val id: String,
    val orgId: String = "",
    val bibleId: String = "",
    val bookId: String = "",
    val chapterId: String = "",
    val reference: String = "",
    val text: String = ""
)

@Serializable
data class BiblesResponse(
    val data: List<BibleVersion>
)

@Serializable
data class BibleVersion(
    val id: String,
    val name: String = "",
    val nameLocal: String = "",
    val abbreviation: String = "",
    val abbreviationLocal: String = "",
    val description: String = "",
    val language: BibleLanguage = BibleLanguage()
)

@Serializable
data class BibleLanguage(
    val id: String = "",
    val name: String = "",
    val nameLocal: String = ""
)
