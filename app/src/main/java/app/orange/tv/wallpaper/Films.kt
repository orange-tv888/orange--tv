package app.orange.tv.wallpaper

import android.content.Context
import app.orange.tv.settings.OrangeSettings
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.view.Display
import android.view.WindowManager
import androidx.core.content.getSystemService
import org.json.JSONObject
import java.util.Locale

/**
 * Apple TV's aerial screensaver films. Orange ships only the catalogue (assets/films.json, built
 * from Apple's public resources-16.tar): addresses and place names. The films themselves stream from
 * Apple's servers while the screensaver runs, the way the Apple TV plays them.
 */
class Film(
    val id: String,
    val label: String,
    val category: String,
    /** 1080p H.264, about 10 Mbps: plays everywhere. */
    val url: String,
    /** 4K SDR HEVC, about 12 Mbps. */
    val uhd: String,
    /** 4K Dolby Vision (profile 5, HEVC 10-bit), about 24 Mbps. */
    val dolbyVision: String,
    /** (second, caption) pairs: tvOS changes the place name as the camera moves on. */
    val captions: List<Pair<Int, String>>,
) {
    fun captionAt(seconds: Int): String = captions.lastOrNull { it.first <= seconds }?.second ?: label

    fun url(quality: FilmQuality): String = when (quality) {
        FilmQuality.DolbyVision -> dolbyVision
        FilmQuality.Uhd -> uhd
        else -> url
    }
}

/** Which of Apple's files the screensaver streams. Auto takes the best this TV can play. */
enum class FilmQuality(val key: String) {
    Auto("auto"), Hd("1080p"), Uhd("4k"), DolbyVision("dolby_vision");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: Auto
    }
}

/**
 * What this TV's decoders and display can play, asked once. A TV's UI is usually 1080p whatever
 * its panel (Xiaomi MiTV-MFTR0 reports a single 1920×1080 mode), so the panel cannot be asked;
 * the decoders can. 4K SDR costs about what the 1080p H.264 files do (12 against 10 Mbps), so a
 * TV that decodes it gets it. Dolby Vision needs a decoder for profile 5 at 4K and a display that
 * lists Dolby Vision: Apple's HDR files are profile 5, which has no HDR10 base layer to fall back
 * on, and decoded by anything else they come out green and purple.
 */
object FilmFormats {
    @Volatile
    private var probed: Set<FilmQuality>? = null

    fun supported(context: Context): Set<FilmQuality> = probed ?: probe(context).also { probed = it }

    /** [chosen], or for Auto the best supported; a choice the TV cannot play falls back too. */
    fun resolve(context: Context, chosen: FilmQuality): FilmQuality {
        val ok = supported(context)
        return if (chosen != FilmQuality.Auto && chosen in ok) chosen
        else listOf(FilmQuality.DolbyVision, FilmQuality.Uhd).firstOrNull { it in ok && (chosen == FilmQuality.Auto || it.ordinal < chosen.ordinal) }
            ?: FilmQuality.Hd
    }

    /**
     * What the aerial wallpaper plays: as chosen, but never Dolby Vision. A TV in Dolby Vision mode
     * shows the tiles over the film with Dolby's picture settings. A screensaver that carries the
     * wallpaper's film on plays this too (Screensaver), so the two are one, with no switch.
     */
    fun forWallpaper(context: Context): FilmQuality =
        resolve(context, OrangeSettings.filmQuality).let { if (it == FilmQuality.DolbyVision) FilmQuality.Uhd else it }

    /**
     * Whether a screensaver of aerials over the aerial wallpaper carries the wallpaper's film on, in
     * its format. Auto does: going to Dolby Vision from the wallpaper's SDR showed black for 0.9–3.5 s
     * (Xiaomi MiTV-MFTR0, one 4K decoder) and most TVs blink as they switch modes. Dolby Vision
     * chosen by hand is played whatever that costs, from the wallpaper's film and moment.
     */
    fun screensaverCarriesWallpaper(context: Context): Boolean =
        !(OrangeSettings.filmQuality == FilmQuality.DolbyVision && FilmQuality.DolbyVision in supported(context))

    /** What a screensaver of aerials starts in; [overWallpaper]: it begins from the aerial wallpaper's film. */
    fun forScreensaver(context: Context, overWallpaper: Boolean): FilmQuality =
        if (overWallpaper && screensaverCarriesWallpaper(context)) forWallpaper(context)
        else resolve(context, OrangeSettings.filmQuality)

    /** One step down, after a decoder failed on [quality]. */
    fun lower(quality: FilmQuality): FilmQuality = when (quality) {
        FilmQuality.DolbyVision -> FilmQuality.Uhd
        else -> FilmQuality.Hd
    }

    private fun probe(context: Context): Set<FilmQuality> {
        val found = mutableSetOf(FilmQuality.Hd)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return found
        val codecs = runCatching { MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder } }.getOrDefault(emptyList())
        fun caps(mime: String) = codecs.mapNotNull { info ->
            if (info.supportedTypes.none { it.equals(mime, ignoreCase = true) }) null
            else runCatching { info.getCapabilitiesForType(mime) }.getOrNull()
        }
        val uhd = caps(MediaFormat.MIMETYPE_VIDEO_HEVC).any { c ->
            runCatching { c.videoCapabilities.areSizeAndRateSupported(3840, 2160, 30.0) }.getOrDefault(false)
        }
        if (uhd) found += FilmQuality.Uhd
        val dvDecoder = caps(MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION).any { c ->
            c.profileLevels.any {
                it.profile == MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheStn &&
                    it.level >= MediaCodecInfo.CodecProfileLevel.DolbyVisionLevelUhd30
            }
        }
        val dvDisplay = runCatching {
            @Suppress("DEPRECATION")
            context.getSystemService<WindowManager>()?.defaultDisplay?.hdrCapabilities?.supportedHdrTypes
                ?.contains(Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION) == true
        }.getOrDefault(false)
        if (uhd && dvDecoder && dvDisplay) found += FilmQuality.DolbyVision
        return found
    }
}

object Films {
    @Volatile
    private var cache: List<Film>? = null

    fun all(context: Context): List<Film> = cache ?: synchronized(this) {
        cache ?: load(context).also { cache = it }
    }

    private fun load(context: Context): List<Film> {
        val zh = Locale.getDefault().language == "zh"
        val root = JSONObject(context.assets.open("films.json").bufferedReader().use { it.readText() })
        val arr = root.getJSONArray("videos")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val caps = o.getJSONArray("captions")
            Film(
                id = o.getString("id"),
                label = o.getString("label"),
                category = o.getString("category"),
                url = o.getString("url"),
                uhd = o.optString("uhd").ifEmpty { o.getString("url") },
                dolbyVision = o.optString("dv").ifEmpty { o.getString("url") },
                captions = (0 until caps.length()).map { j ->
                    val c = caps.getJSONArray(j)
                    c.getInt(0) to (if (zh) c.getString(1) else c.getString(2)).replace(' ', ' ')
                },
            )
        }
    }
}
