package xx.gradus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacesTest {

    @Test
    fun roundTrip() {
        val places = listOf(Place("Одеса", "Одеська область, Україна", 46.4775, 30.7326), Place("Lviv", "", 49.8383, 24.0232))
        assertEquals(places, decodePlaces(encodePlaces(places)))
    }

    @Test
    fun aDamagedEntryCostsOnlyItself() {
        val text = """[{"name":"A","region":"","lat":1.0,"lon":2.0},{"name":"B"},"junk"]"""
        assertEquals(listOf("A"), decodePlaces(text).map { it.name })
        assertTrue(decodePlaces("not json").isEmpty())
    }

    @Test
    fun searchResults() {
        val text = """
            {"results":[
              {"name":"Odesa","latitude":46.47747,"longitude":30.73262,"country":"Ukraine","admin1":"Odesa"},
              {"name":"Odessa","latitude":31.84568,"longitude":-102.36764,"country":"United States","admin1":null},
              {"name":"","latitude":1.0,"longitude":1.0}
            ]}
        """.trimIndent()
        val found = parseSearch(text)
        assertEquals(listOf("Odesa", "Odessa"), found.map { it.name })
        assertEquals("Odesa, Ukraine", found[0].region)
        // A JSON null is a blank, not the word "null".
        assertEquals("United States", found[1].region)
    }

    @Test
    fun noResultsKeyMeansNothingFound() {
        assertTrue(parseSearch("""{"generationtime_ms":0.5}""").isEmpty())
    }

    @Test
    fun keyIsCoordinatesAtFourDecimals() {
        assertEquals("46.4775,30.7326", Place("x", "", 46.47747, 30.73262).key)
    }
}
