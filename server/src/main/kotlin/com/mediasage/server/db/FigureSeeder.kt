package com.mediasage.server.db

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert
import org.slf4j.LoggerFactory

data class FigureSeed(
    val name: String,
    val category: String,
    val century: String,
    val role: String,
    val lifespan: String
)

object FigureSeeder {

    private val logger = LoggerFactory.getLogger(FigureSeeder::class.java)

    private val figures = listOf(
        // Category 1 — Theologians & Reformers
        FigureSeed("Martin Luther", "theologian", "16th", "Theologian & Reformer", "1483-1546"),
        FigureSeed("John Calvin", "theologian", "16th", "Theologian & Reformer", "1509-1564"),
        FigureSeed("Dietrich Bonhoeffer", "theologian", "20th", "Theologian & Martyr", "1906-1945"),
        FigureSeed("Charles Spurgeon", "theologian", "19th", "Preacher & Pastor", "1834-1892"),
        FigureSeed("John Wesley", "theologian", "18th", "Theologian & Evangelist", "1703-1791"),
        FigureSeed("John Wycliffe", "theologian", "14th", "Theologian & Bible Translator", "1320-1384"),
        FigureSeed("William Tyndale", "theologian", "16th", "Bible Translator & Martyr", "1494-1536"),
        FigureSeed("Jonathan Edwards", "theologian", "18th", "Theologian & Revivalist", "1703-1758"),
        FigureSeed("George Whitefield", "theologian", "18th", "Evangelist & Preacher", "1714-1770"),
        FigureSeed("John Knox", "theologian", "16th", "Reformer & Preacher", "1514-1572"),
        FigureSeed("Ulrich Zwingli", "theologian", "16th", "Reformer & Pastor", "1484-1531"),
        FigureSeed("Philip Melanchthon", "theologian", "16th", "Theologian & Scholar", "1497-1560"),
        FigureSeed("Karl Barth", "theologian", "20th", "Theologian & Author", "1886-1968"),
        FigureSeed("John Owen", "theologian", "17th", "Puritan Theologian", "1616-1683"),
        FigureSeed("Richard Baxter", "theologian", "17th", "Puritan Pastor & Author", "1615-1691"),
        FigureSeed("Thomas Cranmer", "theologian", "16th", "Archbishop & Reformer", "1489-1556"),
        FigureSeed("Martin Bucer", "theologian", "16th", "Reformer & Theologian", "1491-1551"),
        FigureSeed("William Carey", "theologian", "18th", "Missionary & Translator", "1761-1834"),
        FigureSeed("A.W. Tozer", "theologian", "20th", "Pastor & Author", "1897-1963"),
        FigureSeed("Jan Hus", "theologian", "15th", "Reformer & Martyr", "1369-1415"),
        FigureSeed("Francis Schaeffer", "theologian", "20th", "Philosopher & Apologist", "1912-1984"),
        FigureSeed("Martyn Lloyd-Jones", "theologian", "20th", "Preacher & Physician", "1899-1981"),

        // Category 2 — Mystics & Contemplatives
        FigureSeed("Watchman Nee", "mystic", "20th", "Church Leader & Author", "1903-1972"),
        FigureSeed("François Fénelon", "mystic", "17th", "Archbishop & Mystic", "1651-1715"),
        FigureSeed("Madame Guyon", "mystic", "17th", "Mystic & Author", "1648-1717"),
        FigureSeed("Thomas à Kempis", "mystic", "15th", "Monk & Author", "1380-1471"),
        FigureSeed("Brother Lawrence", "mystic", "17th", "Monk & Mystic", "1614-1691"),
        FigureSeed("Julian of Norwich", "mystic", "14th", "Anchoress & Mystic", "1342-1416"),
        FigureSeed("John of the Cross", "mystic", "16th", "Mystic & Poet", "1542-1591"),
        FigureSeed("Teresa of Ávila", "mystic", "16th", "Mystic & Reformer", "1515-1582"),
        FigureSeed("Bernard of Clairvaux", "mystic", "12th", "Abbot & Theologian", "1090-1153"),
        FigureSeed("Francis of Assisi", "mystic", "13th", "Friar & Founder", "1181-1226"),
        FigureSeed("Hildegard of Bingen", "mystic", "12th", "Abbess & Visionary", "1098-1179"),
        FigureSeed("Jean-Pierre de Caussade", "mystic", "18th", "Jesuit Priest & Mystic", "1675-1751"),
        FigureSeed("Richard Rolle", "mystic", "14th", "Hermit & Mystic", "1300-1349"),

        // Category 3 — Church Fathers
        FigureSeed("Augustine of Hippo", "church_father", "4th", "Bishop & Church Father", "354-430"),
        FigureSeed("Athanasius", "church_father", "4th", "Bishop & Theologian", "296-373"),
        FigureSeed("John Chrysostom", "church_father", "4th", "Archbishop & Preacher", "347-407"),
        FigureSeed("Origen", "church_father", "3rd", "Theologian & Scholar", "184-253"),
        FigureSeed("Polycarp", "church_father", "2nd", "Bishop & Martyr", "69-155"),
        FigureSeed("Ignatius of Antioch", "church_father", "1st", "Bishop & Martyr", "35-108"),
        FigureSeed("Irenaeus", "church_father", "2nd", "Bishop & Theologian", "130-202"),
        FigureSeed("Clement of Alexandria", "church_father", "2nd", "Theologian & Scholar", "150-215"),
        FigureSeed("Cyprian of Carthage", "church_father", "3rd", "Bishop & Martyr", "200-258"),
        FigureSeed("Basil the Great", "church_father", "4th", "Bishop & Theologian", "330-379"),
        FigureSeed("Gregory of Nyssa", "church_father", "4th", "Bishop & Theologian", "335-395"),
        FigureSeed("Gregory of Nazianzus", "church_father", "4th", "Archbishop & Poet", "329-390"),
        FigureSeed("Ambrose of Milan", "church_father", "4th", "Bishop & Theologian", "340-397"),
        FigureSeed("Jerome", "church_father", "4th", "Scholar & Bible Translator", "347-420"),
        FigureSeed("Justin Martyr", "church_father", "2nd", "Apologist & Martyr", "100-165"),

        // Category 4 — Social Justice & Public Faith
        FigureSeed("Martin Luther King Jr.", "social_justice", "20th", "Pastor & Civil Rights Leader", "1929-1968"),
        FigureSeed("William Wilberforce", "social_justice", "18th", "Politician & Abolitionist", "1759-1833"),
        FigureSeed("Harriet Tubman", "social_justice", "19th", "Abolitionist & Activist", "1822-1913"),
        FigureSeed("Sojourner Truth", "social_justice", "19th", "Abolitionist & Activist", "1797-1883"),
        FigureSeed("Desmond Tutu", "social_justice", "20th", "Archbishop & Activist", "1931-2021"),
        FigureSeed("Frederick Douglass", "social_justice", "19th", "Abolitionist & Statesman", "1818-1895"),
        FigureSeed("Abraham Lincoln", "social_justice", "19th", "President & Statesman", "1809-1865"),
        FigureSeed("Corrie ten Boom", "social_justice", "20th", "Holocaust Survivor & Evangelist", "1892-1983"),
        FigureSeed("Eric Liddell", "social_justice", "20th", "Athlete & Missionary", "1902-1945"),
        FigureSeed("Lord Shaftesbury", "social_justice", "19th", "Politician & Reformer", "1801-1885"),
        FigureSeed("Charles Finney", "social_justice", "19th", "Revivalist & Abolitionist", "1792-1875"),
        FigureSeed("John Newton", "social_justice", "18th", "Clergyman & Abolitionist", "1725-1807"),
        FigureSeed("Olaudah Equiano", "social_justice", "18th", "Abolitionist & Author", "1745-1797"),
        FigureSeed("Howard Thurman", "social_justice", "20th", "Theologian & Civil Rights Mentor", "1899-1981"),
        FigureSeed("John Perkins", "social_justice", "20th", "Pastor & Community Developer", "1930-2023"),

        // Category 5 — Scientists & Intellectuals
        FigureSeed("Isaac Newton", "intellectual", "17th", "Physicist & Theologian", "1643-1727"),
        FigureSeed("Blaise Pascal", "intellectual", "17th", "Mathematician & Philosopher", "1623-1662"),
        FigureSeed("C.S. Lewis", "intellectual", "20th", "Author & Apologist", "1898-1963"),
        FigureSeed("G.K. Chesterton", "intellectual", "20th", "Author & Philosopher", "1874-1936"),
        FigureSeed("Francis Bacon", "intellectual", "16th", "Philosopher & Scientist", "1561-1626"),
        FigureSeed("Galileo Galilei", "intellectual", "16th", "Astronomer & Physicist", "1564-1642"),
        FigureSeed("Johannes Kepler", "intellectual", "16th", "Astronomer & Mathematician", "1571-1630"),
        FigureSeed("George Washington Carver", "intellectual", "19th", "Scientist & Educator", "1864-1943"),
        FigureSeed("Gregor Mendel", "intellectual", "19th", "Friar & Geneticist", "1822-1884"),
        FigureSeed("Francis Collins", "intellectual", "20th", "Geneticist & Author", "1950-present"),
        FigureSeed("Alexis de Tocqueville", "intellectual", "19th", "Historian & Political Thinker", "1805-1859"),
        FigureSeed("Søren Kierkegaard", "intellectual", "19th", "Philosopher & Theologian", "1813-1855"),
        FigureSeed("William James", "intellectual", "19th", "Philosopher & Psychologist", "1842-1910"),
        FigureSeed("Dorothy Sayers", "intellectual", "20th", "Author & Theologian", "1893-1957"),
        FigureSeed("John Lennox", "intellectual", "20th", "Mathematician & Apologist", "1943-present"),

        // Category 6 — Missionaries & Servants
        FigureSeed("Mother Teresa", "missionary", "20th", "Nun & Missionary", "1910-1997"),
        FigureSeed("Hudson Taylor", "missionary", "19th", "Missionary to China", "1832-1905"),
        FigureSeed("Amy Carmichael", "missionary", "19th", "Missionary & Author", "1867-1951"),
        FigureSeed("David Livingstone", "missionary", "19th", "Missionary & Explorer", "1813-1873"),
        FigureSeed("Jim Elliot", "missionary", "20th", "Missionary & Martyr", "1927-1956"),
        FigureSeed("William Booth", "missionary", "19th", "Founder of the Salvation Army", "1829-1912"),
        FigureSeed("Lottie Moon", "missionary", "19th", "Missionary to China", "1840-1912"),
        FigureSeed("Adoniram Judson", "missionary", "19th", "Missionary to Burma", "1788-1850"),
        FigureSeed("Mary Slessor", "missionary", "19th", "Missionary to Africa", "1848-1915"),
        FigureSeed("Count Zinzendorf", "missionary", "18th", "Bishop & Missions Leader", "1700-1760"),
        FigureSeed("George Müller", "missionary", "19th", "Evangelist & Orphan Care Pioneer", "1805-1898"),
        FigureSeed("Gladys Aylward", "missionary", "20th", "Missionary to China", "1902-1970"),
        FigureSeed("C.T. Studd", "missionary", "19th", "Missionary & Athlete", "1860-1931"),
        FigureSeed("Jonathan Goforth", "missionary", "19th", "Missionary to China", "1859-1936"),
        FigureSeed("Elisabeth Elliot", "missionary", "20th", "Missionary & Author", "1926-2015"),
        FigureSeed("Frank Laubach", "missionary", "20th", "Missionary & Literacy Pioneer", "1884-1970"),
        FigureSeed("Samuel Zwemer", "missionary", "19th", "Missionary to the Muslim World", "1867-1952"),
        FigureSeed("Nate Saint", "missionary", "20th", "Missionary Pilot & Martyr", "1923-1956"),
        FigureSeed("John G. Paton", "missionary", "19th", "Missionary to the Pacific Islands", "1824-1907"),
        FigureSeed("Andrew Murray", "missionary", "19th", "Pastor & Author", "1828-1917")
    )

    suspend fun seed(httpClient: HttpClient) = withContext(Dispatchers.IO) {
        val existingCount = transaction { FigureTable.selectAll().count() }
        if (existingCount >= figures.size) {
            logger.info("Figures already seeded ($existingCount records) — skipping")
            return@withContext
        }

        logger.info("Seeding ${figures.size} figures...")
        figures.forEachIndexed { index, figure ->
            val bio = fetchWikipediaBio(httpClient, figure.name)
            transaction {
                FigureTable.upsert(FigureTable.name) {
                    it[name] = figure.name
                    it[category] = figure.category
                    it[century] = figure.century
                    it[role] = figure.role
                    it[lifespan] = figure.lifespan
                    it[FigureTable.bio] = bio
                    it[isEnabled] = true
                }
            }
            logger.info("Seeded (${index + 1}/${figures.size}): ${figure.name}")
            if (index < figures.size - 1) delay(300)
        }
        logger.info("Figure seeding complete")
    }

    private suspend fun fetchWikipediaBio(httpClient: HttpClient, name: String): String {
        return try {
            val response = httpClient.get("https://en.wikipedia.org/w/api.php") {
                parameter("action", "query")
                parameter("titles", name)
                parameter("prop", "extracts")
                parameter("exintro", "true")
                parameter("explaintext", "true")
                parameter("format", "json")
            }
            val json = response.body<JsonObject>()
            val pages = json["query"]?.jsonObject?.get("pages")?.jsonObject
            val page = pages?.values?.firstOrNull()?.jsonObject
            page?.get("extract")?.jsonPrimitive?.content?.trim() ?: ""
        } catch (e: Exception) {
            logger.warn("Failed to fetch Wikipedia bio for $name: ${e.message}")
            ""
        }
    }
}
