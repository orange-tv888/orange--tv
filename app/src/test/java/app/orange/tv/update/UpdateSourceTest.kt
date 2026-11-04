package app.orange.tv.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class UpdateSourceTest {

    @Test
    fun `a bare version is a release with nothing else`() {
        val release = UpdateSource.parse("26.4\n")
        assertEquals("26.4", release?.version)
        assertNull(release?.apk)
        assertTrue(release!!.notes.isEmpty())
    }

    @Test
    fun `a v prefix is accepted, as the old website wrote it`() {
        assertEquals("26.4", UpdateSource.parse("v26.4")?.version)
    }

    @Test
    fun `a byte order mark does not stop the version`() {
        assertEquals("26.4", UpdateSource.parse("\uFEFF26.4")?.version)
    }

    @Test
    fun `the release note is read whole`() {
        val json = """
            {
              "version": "26.4",
              "apk": "https://cdn.jsdelivr.net/gh/orange-tv888/orange--tv@main/update/orange.apk",
              "notes": ["打赏页支持长按返回键关闭", "一键优化更懂后台进程"]
            }
        """.trimIndent()
        val release = UpdateSource.parse(json)
        assertEquals("26.4", release?.version)
        assertEquals(
            "https://cdn.jsdelivr.net/gh/orange-tv888/orange--tv@main/update/orange.apk",
            release?.apk,
        )
        assertEquals(listOf("打赏页支持长按返回键关闭", "一键优化更懂后台进程"), release?.notes)
    }

    @Test
    fun `a release note without notes still names its version`() {
        val release = UpdateSource.parse("""{"version":"26.4"}""")
        assertEquals("26.4", release?.version)
        assertTrue(release!!.notes.isEmpty())
    }

    @Test
    fun `only https apk addresses are taken`() {
        assertNull(UpdateSource.parse("""{"version":"26.4","apk":"http://example.com/a.apk"}""")?.apk)
        assertNull(UpdateSource.parse("""{"version":"26.4","apk":"not a url"}""")?.apk)
    }

    @Test
    fun `more notes than a TV can show are cut to four`() {
        val json = """{"version":"26.4","notes":["一","二","三","四","五"]}"""
        assertEquals(listOf("一", "二", "三", "四"), UpdateSource.parse(json)?.notes)
    }

    @Test
    fun `notes may be objects, as a hand-written file sometimes has them`() {
        val json = """{"version":"26.4","notes":[{"text":"改了打赏页"}]}"""
        assertEquals(listOf("改了打赏页"), UpdateSource.parse(json)?.notes)
    }

    @Test
    fun `a version is required`() {
        assertNull(UpdateSource.parse("""{"notes":["没有版本号"]}"""))
        assertNull(UpdateSource.parse("""{"version":"latest"}"""))
    }

    @Test
    fun `an error page is not a release`() {
        assertNull(UpdateSource.parse("<html><body>404 Not Found</body></html>"))
        assertNull(UpdateSource.parse(""))
        assertNull(UpdateSource.parse("   "))
    }

    @Test
    fun `the built-in sources all point at the same repository`() {
        val urls = UpdateSource.VERSION_URLS + UpdateSource.APK_URLS
        assertTrue(urls.all { it.contains(UpdateSource.REPO) })
        assertTrue(urls.all { it.startsWith("https://") })
        // The mirror first: it is the one that answers from China, where GitHub does not.
        assertTrue(UpdateSource.VERSION_URLS.first().startsWith("https://ghproxy.net/https://raw.githubusercontent.com/"))
        assertTrue(UpdateSource.APK_URLS.first().startsWith("https://ghproxy.net/https://raw.githubusercontent.com/"))
    }
}
