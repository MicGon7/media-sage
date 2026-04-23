package com.mediasage.di

import com.mediasage.data.remote.EncourageResultDto
import com.mediasage.data.remote.NewsArticleDto

/** Temporary mock data toggle for physical device demos without a server. */
object MockConfig {
    var useMockData: Boolean = false
}

object MockData {

    val headlines = listOf(
        NewsArticleDto(
            uuid = "1",
            title = "Community Gardens Transform Urban Neighborhoods Across America",
            description = "Local residents report increased connection and purpose through collaborative food growing initiatives.",
            snippet = "Local residents report increased connection and purpose through collaborative food growing initiatives that are reshaping urban communities...",
            url = "https://apnews.com/community-gardens",
            imageUrl = "https://images.unsplash.com/photo-1416879595882-3373a0480b5b?w=800",
            source = "AP News",
            categories = listOf("society")
        ),
        NewsArticleDto(
            uuid = "2",
            title = "New Research Links Daily Gratitude Practice to Significant Mental Health Improvements",
            description = "Peer-reviewed study reveals daily gratitude practices reduce anxiety and depression symptoms by 30%.",
            snippet = "A landmark peer-reviewed study reveals daily gratitude practices reduce anxiety and depression symptoms by 30%, offering new hope...",
            url = "https://npr.org/gratitude-study",
            imageUrl = "https://images.unsplash.com/photo-1506126613408-eca07ce68773?w=800",
            source = "NPR",
            categories = listOf("health")
        ),
        NewsArticleDto(
            uuid = "3",
            title = "Bipartisan Coalition Introduces Comprehensive Poverty Relief Legislation",
            description = "Lawmakers from both parties collaborate on sweeping legislation to support vulnerable communities.",
            snippet = "In a rare show of unity, lawmakers from both parties collaborate on sweeping legislation to support vulnerable communities nationwide...",
            url = "https://cnn.com/poverty-relief-bill",
            imageUrl = "https://images.unsplash.com/photo-1529107386315-e1a2ed48a620?w=800",
            source = "CNN",
            categories = listOf("politics")
        ),
        NewsArticleDto(
            uuid = "4",
            title = "Pacific Ocean Cleanup Initiative Removes Record Amount of Plastic Waste",
            description = "Innovative technology removes millions of pounds of plastic from the Pacific, inspiring global environmental movement.",
            snippet = "Innovative technology removes millions of pounds of plastic from the Pacific Ocean, inspiring a global environmental movement...",
            url = "https://bbc.com/ocean-cleanup",
            imageUrl = "https://images.unsplash.com/photo-1484291470158-b8f8d608850d?w=800",
            source = "BBC",
            categories = listOf("environment")
        ),
        NewsArticleDto(
            uuid = "5",
            title = "Schools Nationwide Integrate Compassion and Empathy Into Core Curriculum",
            description = "Districts adopt evidence-based empathy and kindness programs with remarkable academic and social results.",
            snippet = "Districts nationwide adopt evidence-based empathy and kindness programs with remarkable academic and social results...",
            url = "https://usatoday.com/compassion-curriculum",
            imageUrl = "https://images.unsplash.com/photo-1503676260728-1c00da094a0b?w=800",
            source = "USA Today",
            categories = listOf("education")
        ),
    )

    private val encourageResults = mapOf(
        "1" to EncourageResultDto(
            summary = null,
            quoteText = "The earth is the Lord's and the fullness thereof. Oh, that men would praise the Lord for his goodness, and declare the wonders that he doeth for the children of men!",
            figureName = "John Calvin",
            figureRole = "Reformer & Theologian",
            scriptureReference = "Genesis 2:15",
            scriptureText = "The Lord God took the man and put him in the Garden of Eden to work it and take care of it.",
            explanation = "From the very beginning, God entrusted humanity with the cultivation and care of the earth. Community gardens echo this original calling, inviting neighbors to work side by side, tending creation together and sharing its abundance.",
            connectionThemes = listOf("creation stewardship", "community", "shared labor"),
            matchTheme = "Cultivating Shalom",
            tone = "EXHORTATION"
        ),
        "2" to EncourageResultDto(
            summary = null,
            quoteText = "He is no fool who gives what he cannot keep to gain that which he cannot lose.",
            figureName = "Jim Elliot",
            figureRole = "Missionary & Martyr",
            scriptureReference = "1 Thessalonians 5:18",
            scriptureText = "Give thanks in all circumstances; for this is God's will for you in Christ Jesus.",
            explanation = "Science now confirms what Scripture has taught for millennia — a grateful heart transforms the mind. When we practice thankfulness, we align ourselves with God's design for human flourishing.",
            connectionThemes = listOf("gratitude", "mental health", "divine design"),
            matchTheme = "Grateful Hearts",
            tone = "EXHORTATION"
        ),
        "3" to EncourageResultDto(
            summary = null,
            quoteText = "Injustice anywhere is a threat to justice everywhere. We are caught in an inescapable network of mutuality, tied in a single garment of destiny.",
            figureName = "Martin Luther King Jr.",
            figureRole = "Pastor & Civil Rights Leader",
            scriptureReference = "Proverbs 31:8-9",
            scriptureText = "Speak up for those who cannot speak for themselves, for the rights of all who are destitute. Speak up and judge fairly; defend the rights of the poor and needy.",
            explanation = "When leaders across divides unite to address poverty, they embody the biblical call to defend the vulnerable. This bipartisan effort reflects God's heart for justice and the dignity of every person.",
            connectionThemes = listOf("justice", "unity", "advocacy for the poor"),
            matchTheme = "Justice & Unity",
            tone = "EXHORTATION"
        ),
        "4" to EncourageResultDto(
            summary = null,
            quoteText = "Start by doing what's necessary; then do what's possible; and suddenly you are doing the impossible.",
            figureName = "Francis of Assisi",
            figureRole = "Friar & Founder",
            scriptureReference = "Revelation 21:5",
            scriptureText = "He who was seated on the throne said, 'I am making everything new!'",
            explanation = "The restoration of creation mirrors God's own redemptive work. What seemed impossible — cleaning the vast ocean — becomes possible when faithful stewards act. This echoes God's promise to make all things new.",
            connectionThemes = listOf("restoration", "stewardship", "hope"),
            matchTheme = "Renewal & Hope",
            tone = "EXHORTATION"
        ),
        "5" to EncourageResultDto(
            summary = null,
            quoteText = "Education without values, as useful as it is, seems rather to make man a more clever devil.",
            figureName = "C.S. Lewis",
            figureRole = "Author & Apologist",
            scriptureReference = "Proverbs 22:6",
            scriptureText = "Start children off on the way they should go, and even when they are old they will not turn from it.",
            explanation = "Teaching compassion alongside academics fulfills the biblical mandate to train children in the way they should go. Lewis understood that knowledge without character forms the mind but not the soul.",
            connectionThemes = listOf("education", "character formation", "compassion"),
            matchTheme = "Heart & Mind",
            tone = "EXHORTATION"
        ),
    )

    fun encourageResultForHeadline(headlineUuid: String): EncourageResultDto =
        encourageResults[headlineUuid] ?: encourageResults["1"]!!
}
