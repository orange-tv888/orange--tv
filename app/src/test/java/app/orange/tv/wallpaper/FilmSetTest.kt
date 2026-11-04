package app.orange.tv.wallpaper

import android.app.Application
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FilmSetTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private var day = "2026-10-29"

    @Before
    fun setUp() {
        app.getSharedPreferences("orange_set", 0).edit().clear().commit()
        FilmSet.today = { day }
    }

    @After
    fun tearDown() {
        FilmSet.today = { java.time.LocalDate.now().toString() }
    }

    @Test
    fun startsWithOneFilmAndNothingNewTheSameDay() {
        assertEquals(1, FilmSet.films(app).size)
        assertFalse(FilmSet.due(app))
        assertNull(FilmSet.takeDaily(app))
    }

    @Test
    fun aNewDayBringsOneNewFilmFirst() {
        val first = FilmSet.films(app).single()
        day = "2026-10-30"
        assertTrue(FilmSet.due(app))
        val new = FilmSet.takeDaily(app)
        assertNotNull(new)
        new!!
        assertEquals(listOf(new.id, first.id), FilmSet.films(app).map { it.id })
        // Only one a day.
        assertNull(FilmSet.takeDaily(app))
    }

    @Test
    fun theNewFilmIsTheOneChosenAhead() {
        FilmSet.films(app)
        day = "2026-10-30"
        val ahead = FilmSet.nextNew(app)
        assertEquals(ahead.id, FilmSet.takeDaily(app)?.id)
    }

    @Test
    fun keepsAtMostThreeNewestFirstWithoutRepeats() {
        FilmSet.films(app)
        val added = (1..4).map { FilmSet.addNew(app).id }
        val kept = FilmSet.films(app).map { it.id }
        assertEquals(FilmSet.MAX, kept.size)
        assertEquals(added.reversed().take(FilmSet.MAX), kept)
        assertEquals(kept.size, kept.toSet().size)
    }

    @Test
    fun playlistStartsAtTheCurrentFilmAndGoesRound() {
        FilmSet.films(app)
        FilmSet.addNew(app)
        FilmSet.addNew(app)
        val set = FilmSet.films(app).map { it.id }
        val list = FilmSet.playlist(app, set[1]).map { it.id }
        assertEquals(listOf(set[1], set[2], set[0]), list)
    }

    @Test
    fun aFilmFromOutsideTheSetJoinsIt() {
        FilmSet.films(app)
        val other = Films.all(app).first { it.id !in FilmSet.films(app).map { f -> f.id } }
        val list = FilmSet.playlist(app, other.id)
        assertEquals(other.id, list.first().id)
        assertTrue(FilmSet.films(app).any { it.id == other.id })
    }
}
