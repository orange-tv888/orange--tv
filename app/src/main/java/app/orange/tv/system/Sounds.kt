package app.orange.tv.system

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import app.orange.tv.R
import app.orange.tv.settings.OrangeSettings
import app.orange.tv.settings.UiSounds

/**
 * Interface sounds, as tvOS plays them: a tick when focus moves or a slider steps, a click on
 * select and on a long press, one on Back, a bump at the edge of the grid or a slider.
 *
 * Two sets, chosen in Personalization: the TV's own sound effects (AudioManager.playSoundEffect),
 * so each TV sounds as its own menus do, which the system's key sound switch also silences (the
 * Xiaomi MFTR0 plays Android TV's android_TV_scroll_* and android_TV_select); or Orange's own
 * (tools/make-sounds.py before 1.27), for TVs that ship with key sounds off (#169).
 *
 * Focus sounds play only for a move the remote asked for: focus that lands somewhere because a
 * panel opened or Home reset is silent, as on tvOS. One tick per key press, however many controls
 * see focus change on the way.
 */
object Sounds {
    private var audio: AudioManager? = null
    private var app: Context? = null
    private var pool: SoundPool? = null
    private var move = 0
    private var select = 0
    private var back = 0
    private var limit = 0
    private var bootPool: SoundPool? = null
    private var boot = 0
    private var bootReady = false
    private var bootWanted = false
    private var lastDpadAt = 0L
    private var lastMove = AudioManager.FX_FOCUS_NAVIGATION_DOWN
    private var tickedFor = -1L

    fun init(context: Context) {
        if (audio == null) audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (app == null) app = context.applicationContext
        prepare()
    }

    /** Loads Orange's own set ahead of the first press when it is the one chosen; loading takes a moment. */
    fun prepare() {
        if (OrangeSettings.uiSounds == UiSounds.Orange) ownPool()
    }

    /** Orange's own set, loaded the first time it is needed. */
    private fun ownPool(): SoundPool? {
        pool?.let { return it }
        val context = app ?: return null
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        return SoundPool.Builder().setMaxStreams(3).setAudioAttributes(attrs).build().also {
            move = it.load(context, R.raw.ui_move, 1)
            select = it.load(context, R.raw.ui_select, 1)
            back = it.load(context, R.raw.ui_back, 1)
            limit = it.load(context, R.raw.ui_limit, 1)
            pool = it
        }
    }

    /** Every key the activity sees, before Compose does. */
    fun onKey(event: KeyEvent) {
        if (event.action != KeyEvent.ACTION_DOWN) return
        val move = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> AudioManager.FX_FOCUS_NAVIGATION_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> AudioManager.FX_FOCUS_NAVIGATION_DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> AudioManager.FX_FOCUS_NAVIGATION_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> AudioManager.FX_FOCUS_NAVIGATION_RIGHT
            KeyEvent.KEYCODE_BACK -> {
                if (event.repeatCount == 0) play(if (Build.VERSION.SDK_INT >= 33) AudioManager.FX_BACK else AudioManager.FX_KEY_CLICK, Kind.Back)
                return
            }
            else -> return
        }
        lastDpadAt = event.eventTime
        lastMove = move
    }

    /** A control took focus. */
    fun focusMoved() {
        val at = lastDpadAt
        if (at == tickedFor || SystemClock.uptimeMillis() - at > 250) return
        tickedFor = at
        play(lastMove, Kind.Move)
    }

    /** A D-pad press that had nowhere to go. */
    fun limit() {
        tickedFor = lastDpadAt
        play(AudioManager.FX_KEYPRESS_INVALID, Kind.Limit)
    }

    fun select() = play(AudioManager.FX_KEY_CLICK, Kind.Select)

    /**
     * The start-up chime, struck on the frame the word in BootWordIntro lands.
     *
     * It belongs to the opening, not to Home appearing and not to the TV switching on: the piece is
     * shown once per process, and the chime is the half second of it that is heard.
     *
     * It has its own pool: [ownPool] is only filled when the Orange set is chosen, and this sound
     * is wanted with either set — it is the opening, not a key press. Off stays off: a TV whose
     * sounds were turned off should not announce itself either.
     */
    fun boot() {
        if (OrangeSettings.uiSounds == UiSounds.Off) return
        prepareBoot()
        bootWanted = true
        if (bootReady) strike()
    }

    /**
     * Begins loading the chime, so that [boot] can strike it without waiting.
     *
     * The load is asynchronous and takes a few hundred milliseconds: called when the opening starts
     * and struck about a second and a half later, the sample is there by then. Loading at the moment
     * of the strike would put the sound in the room late, exactly on the one occasion it exists for.
     */
    fun prepareBoot() {
        if (bootPool != null || OrangeSettings.uiSounds == UiSounds.Off) return
        val context = app ?: return
        // USAGE_MEDIA, not the sonification usage the key sounds use: this is the start of
        // watching something, it should follow the volume the TV was left at, and a TV muted at
        // night must stay quiet.
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val pool = SoundPool.Builder().setMaxStreams(1).setAudioAttributes(attrs).build()
        boot = pool.load(context, R.raw.boot, 1)
        pool.setOnLoadCompleteListener { p, id, status ->
            if (status == 0 && id == boot) {
                bootReady = true
                // A strike asked for before the sample arrived is played now, not dropped.
                if (bootWanted) strike()
            }
        }
        bootPool = pool
    }

    private fun strike() {
        val pool = bootPool ?: return
        if (!bootWanted || boot == 0) return
        bootWanted = false
        pool.play(boot, 1f, 1f, 1, 0, 1f)
    }

    /** A slider moved one step (volume, brightness). */
    fun step() = play(lastMove, Kind.Move)

    private enum class Kind { Move, Select, Back, Limit }

    /**
     * The volume form, with the key sound switch checked here as the one-argument form does: Hisense
     * VIDAA_TV (MT9653, Android 11) changed that one to take only its own effects (10 to 18), so the
     * standard ones played nothing (issue #262). -1 is the volume the one-argument form uses.
     */
    private fun playTvEffect(effect: Int) {
        val context = app ?: return
        val on = runCatching { Settings.System.getInt(context.contentResolver, Settings.System.SOUND_EFFECTS_ENABLED, 1) != 0 }
            .getOrDefault(true)
        if (on) audio?.playSoundEffect(effect, -1f)
    }

    private fun play(effect: Int, kind: Kind) {
        when (OrangeSettings.uiSounds) {
            UiSounds.Off -> Unit
            UiSounds.Tv -> playTvEffect(effect)
            UiSounds.Orange -> {
                val p = ownPool() ?: return
                val (id, volume) = when (kind) {
                    Kind.Move -> move to 0.7f
                    Kind.Select -> select to 1f
                    Kind.Back -> back to 0.9f
                    Kind.Limit -> limit to 0.9f
                }
                if (id != 0) p.play(id, volume, volume, 1, 0, 1f)
            }
        }
    }
}
