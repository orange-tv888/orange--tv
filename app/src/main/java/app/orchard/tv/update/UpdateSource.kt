package app.orchard.tv.update

import org.json.JSONArray
import org.json.JSONObject

/**
 * Where Orange looks for a newer version, and what a release note looks like.
 *
 * The release is published on GitHub, but github.com itself does not answer from Chinese home
 * broadband: measured on the TV, TCP 443 to github.com, raw.githubusercontent.com and
 * cdn.jsdelivr.net all time out, while a GitHub mirror does answer. So the app asks a mirror first
 * and only falls back to the direct URLs, which work from networks where GitHub itself is fine.
 * The files live in this repository under update/, so pushing is all a release needs.
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

    /**
     * GitHub mirrors reachable from mainland China. Prefixed to a raw.githubusercontent URL they
     * return the same bytes. Both are volunteer services, so neither is the only way in: the
     * direct URLs below are tried after them.
     *
     * Order matters and was measured, not guessed: gh-proxy.com kept serving a cached
     * version.json long after the push (its own cache outlives the Cloudflare 60 s), so a release
     * took minutes to show up. ghproxy.net answers with the new file at once.
     */
    private val MIRRORS = listOf("https://ghproxy.net/", "https://gh-proxy.com/")

    private fun raw(path: String) = "https://raw.githubusercontent.com/$REPO/main/$path"

    private fun jsdelivr(path: String) = "https://cdn.jsdelivr.net/gh/$REPO@main/$path"

    /** version.json, in the order they are tried. */
    val VERSION_URLS: List<String> =
        MIRRORS.map { it + raw("update/version.json") } + raw("update/version.json") + jsdelivr("update/version.json")

    /** Where the APK is fetched from when the JSON names no apk. */
    val APK_URLS: List<String> =
        MIRRORS.map { it + raw("update/orange.apk") } + raw("update/orange.apk") + jsdelivr("update/orange.apk")

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
