package app.orchard.tv.update

import org.json.JSONArray
import org.json.JSONObject

/**
 * Where Orange looks for a newer version, and what a release note looks like.
 *
 * The release is published on GitHub, but github.com itself is unreliable from Chinese home
 * broadband — the update page would say "check failed" most of the time. So the same two files are
 * also kept in this repository under update/, which jsDelivr serves from Chinese CDN nodes. The
 * order below is what the app tries: jsDelivr first, GitHub second, raw.githubusercontent last.
 *
 * A version.json names the APK it belongs to. That is deliberate: moving to another host later
 * means editing the JSON, not shipping a new build. When it names none, [APK_URLS] is used.
 *
 * Whatever the JSON says, an APK is only installed when it carries this app's package name and
 * signature (Updates.sameSigner), so a tampered file — or a hijacked JSON — cannot get in.
 */
object UpdateSource {

    /**
     * The GitHub repository that publishes Orange. Change it here and nowhere else.
     * Files it must carry, refreshed on every release:
     *
     *   update/version.json   the release note below
     *   update/orange.apk     the build, always the newest one
     */
    const val REPO = "orange-tv888/orange--tv"

    /** version.json, in the order they are tried. */
    val VERSION_URLS: List<String> = listOf(
        "https://cdn.jsdelivr.net/gh/$REPO@main/update/version.json",
        "https://github.com/$REPO/releases/latest/download/version.json",
        "https://raw.githubusercontent.com/$REPO/main/update/version.json",
    )

    /** Where the APK is fetched from when the JSON names no apk. */
    val APK_URLS: List<String> = listOf(
        "https://cdn.jsdelivr.net/gh/$REPO@main/update/orange.apk",
        "https://github.com/$REPO/releases/latest/download/orange.apk",
    )

    /** At most this many lines of "what changed" are shown; more than that is not read on a TV. */
    const val MAX_NOTES = 4

    /** One published release: its number, where its APK is, and what changed. */
    data class Release(
        val version: String,
        /** An https URL from the JSON, or null to fall back to [APK_URLS]. */
        val apk: String?,
        val notes: List<String>,
    )

    private val VERSION = Regex("\\d+(\\.\\d+)*")

    /**
     * The release in [raw], or null when it is not one.
     *
     * A bare version number ("26.4") is still accepted: it is what the old website served, what a
     * hand-written file looks like, and what a fetch of the wrong page returns.
     */
    fun parse(raw: String): Release? {
        val text = raw.trim().removePrefix("\uFEFF")
        if (text.isEmpty()) return null
        version(text)?.let { return Release(it, null, emptyList()) }
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return null
        val version = version(json.optString("version")) ?: return null
        val apk = json.optString("apk").trim().takeIf { it.startsWith("https://") }
        return Release(version, apk, notes(json))
    }

    private fun version(value: String): String? {
        val v = value.trim().removePrefix("v").removePrefix("V")
        return v.takeIf { VERSION.matches(it) }
    }

    private fun notes(json: JSONObject): List<String> {
        val array = json.optJSONArray("notes") ?: return emptyList()
        return strings(array).filter { it.isNotBlank() }.take(MAX_NOTES)
    }

    /** ["a", "b"] and [{"text": "a"}] both reach here: the file is written by hand, per release. */
    private fun strings(array: JSONArray): List<String> = buildList {
        for (i in 0 until array.length()) {
            when (val item = array.opt(i)) {
                is String -> add(item)
                is JSONObject -> add(item.optString("text"))
            }
        }
    }
}
