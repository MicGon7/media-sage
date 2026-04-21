package com.mediasage.data.local.db

import com.mediasage.data.local.entity.FigureEntity

object InitialFigures {

    val all = listOf(
        FigureEntity(
            id = 1,
            name = "Dietrich Bonhoeffer",
            category = "theologian",
            century = "20th",
            description = "German pastor and theologian who resisted the Nazi regime. " +
                "Executed for his role in a plot to assassinate Hitler.",
            role = "Theologian & Martyr",
            lifespan = "1906-1945"
        ),
        FigureEntity(
            id = 2,
            name = "Watchman Nee",
            category = "theologian",
            century = "20th",
            description = "Chinese church leader and Christian author. " +
                "His books on the spiritual life, including The Normal Christian Life, " +
                "have influenced millions worldwide.",
            role = "Church Leader & Author",
            lifespan = "1903-1972"
        ),
        FigureEntity(
            id = 3,
            name = "Julian of Norwich",
            category = "mystic",
            century = "14th",
            description = "English anchoress and theologian who received profound visions " +
                "of divine love. Her \"Revelations of Divine Love\" is the earliest " +
                "surviving English-language work by a woman.",
            role = "Medieval Mystic",
            lifespan = "1342-1416"
        ),
        FigureEntity(
            id = 4,
            name = "Dorothy Day",
            category = "modern",
            century = "20th",
            description = "American journalist and Catholic convert who co-founded " +
                "the Catholic Worker Movement.",
            role = "Social Activist & Servant",
            lifespan = "1897-1980"
        ),
        FigureEntity(
            id = 5,
            name = "Pope Francis",
            category = "modern",
            century = "21st",
            description = "First Jesuit pope and first from the Americas. " +
                "Known for his humility, emphasis on mercy, and advocacy for the poor.",
            role = "Bishop of Rome",
            lifespan = "1936-present"
        ),
        FigureEntity(
            id = 6,
            name = "Augustine of Hippo",
            category = "theologian",
            century = "4th",
            description = "Bishop, philosopher, and one of the most influential " +
                "Church Fathers. Author of Confessions and The City of God.",
            role = "Bishop & Church Father",
            lifespan = "354-430"
        ),
        FigureEntity(
            id = 7,
            name = "Corrie ten Boom",
            category = "modern",
            century = "20th",
            description = "Dutch watchmaker who helped many Jews escape the Holocaust. " +
                "Imprisoned at Ravensbruck concentration camp.",
            role = "Holocaust Survivor & Evangelist",
            lifespan = "1892-1983"
        ),
        FigureEntity(
            id = 8,
            name = "Francis of Assisi",
            category = "mystic",
            century = "13th",
            description = "Italian friar who founded the Franciscan Order. " +
                "Known for his love of nature, poverty, and peace.",
            role = "Friar & Founder",
            lifespan = "1181-1226"
        ),
        FigureEntity(
            id = 9,
            name = "C.S. Lewis",
            category = "theologian",
            century = "20th",
            description = "British writer and lay theologian. Author of Mere Christianity, " +
                "The Screwtape Letters, and The Chronicles of Narnia.",
            role = "Author & Apologist",
            lifespan = "1898-1963"
        ),
        FigureEntity(
            id = 10,
            name = "Mother Teresa",
            category = "modern",
            century = "20th",
            description = "Albanian-Indian nun who founded the Missionaries of Charity. " +
                "Dedicated her life to serving the poorest of the poor in Calcutta.",
            role = "Nun & Missionary",
            lifespan = "1910-1997"
        ),
    )
}
