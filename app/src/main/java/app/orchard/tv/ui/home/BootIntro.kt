package app.orchard.tv.ui.home

import android.content.Context
import android.graphics.BitmapFactory
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.orchard.tv.R
import app.orchard.tv.ui.theme.LocalReducedMotion
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The word Orange falling in as two sheets of glass, played over Home once, at the start of the
 * app.
 *
 * Why it exists: after a boot the remote is not an input device yet — it registers three to four
 * seconds late (measured on a MiTV-ASTP0: 3.3 s cold, 4.2 s from standby), and until it does the
 * keys go nowhere while Home sits there looking ready. This piece gives those seconds something to
 * say, and it is drawn as a skin over Home that is already laid out: the frame it leaves on is the
 * very Home behind it, so the first press after it lands works at once.
 *
 * It is a full-screen cover, never a tile-by-tile animation: Home renders underneath from its first
 * frame, and the cover only has to lift.
 */
@Composable
fun BootWordIntro(onFinished: () -> Unit) {
    val context = LocalContext.current
    val sprite = remember {
        runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.boot_word)?.asImageBitmap()
        }.getOrNull()
    }
    val sweep = remember {
        runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.boot_word_sweep)?.asImageBitmap()
        }.getOrNull()
    }
    val finished = rememberUpdatedState(onFinished)
    // No word to show, or motion turned off: nothing to hold the screen for.
    if (sprite == null || sweep == null || LocalReducedMotion.current) {
        LaunchedEffect(Unit) { finished.value() }
        return
    }

    var n by remember { mutableFloatStateOf(0f) }
    var hurry by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }

    // The remote landing as an input device ends the wait the piece is covering for.
    DisposableEffect(Unit) {
        val manager = context.getSystemService(Context.INPUT_SERVICE) as? InputManager
        val listener = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) {
                hurry = true
            }

            override fun onInputDeviceRemoved(deviceId: Int) = Unit

            override fun onInputDeviceChanged(deviceId: Int) = Unit
        }
        manager?.registerInputDeviceListener(listener, Handler(Looper.getMainLooper()))
        onDispose { manager?.unregisterInputDeviceListener(listener) }
    }

    LaunchedEffect(Unit) {
        var last = 0L
        while (n < 1f) {
            var step = 0f
            withFrameNanos { now ->
                if (last != 0L) step = (now - last) / 1_000_000_000f
                last = now
            }
            // A remote that lands early shortens only the tail: the word still settles first, so
            // the piece never reads as a flicker.
            n = (n + step / DUR * if (hurry && n >= 0.62f) 3f else 1f).coerceAtMost(1f)
        }
        done = true
        finished.value()
    }

    Canvas(Modifier.fillMaxSize()) {
        if (done) return@Canvas
        val scale = min(size.width / DESIGN_W, size.height / DESIGN_H)
        val ox = (size.width - DESIGN_W * scale) / 2f
        val oy = (size.height - DESIGN_H * scale) / 2f

        // The word lets go first, the black behind it a beat later: a half-faded word over an
        // already-lit Home reads as a ghost of itself.
        val veil = 1f - clamp01((n - 0.944f) / 0.056f)
        val mark = 1f - clamp01((n - 0.882f) / 0.062f)
        if (veil > 0f) drawRect(Color.Black, alpha = veil)
        if (mark <= 0f) return@Canvas

        withTransform({
            translate(ox, oy)
            scale(scale, scale, Offset.Zero)
        }) {
            drawWord(sprite, n, mark)
            drawProgress(n, mark)
            drawSweep(sweep, n, mark)
        }
    }
}

/* ---------- 时间轴：与 animpack/render_word.py 同一套数 ---------- */

private const val DUR = 3.6f
private const val DESIGN_W = 1920f
private const val DESIGN_H = 1080f

private const val SPR_X = 540f
private const val SPR_Y = 300f
private const val SPR_W = 840f
private const val SPR_H = 390f

private const val WORD_CX = 960f
private const val WORD_CY = 466f
private const val BAR_W = 380f
private const val BAR_Y = 701f

private const val DROP_T0 = 0.050f
private const val DROP_GAP = 0.105f
private const val DROP_DUR = 0.280f
private const val DROP_DIST = 640f

private val LAYER_ALPHA = floatArrayOf(0.86f, 1f)
private val LAYER_TILT = floatArrayOf(2.6f, -2.0f)

/** The sheet underneath: the same glass at 70% brightness, so the second one reads as lighting it. */
private val DIM = ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(0.70f, 0.70f, 0.70f, 1f) })

private fun clamp01(v: Float) = if (v < 0f) 0f else if (v > 1f) 1f else v

private fun easeOutCubic(t: Float): Float {
    val r = 1f - t
    return 1f - r * r * r
}

private fun easeOutQuint(t: Float): Float {
    val r = 1f - t
    return 1f - r * r * r * r * r
}

private fun easeInOutCubic(t: Float): Float =
    if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).let { it * it * it } / 2f

private fun DrawScope.drawWord(
    sprite: ImageBitmap,
    n: Float,
    group: Float,
) {
    // The settle bounce, the whole mark together.
    val bob = if (n in 0.46f..0.56f) -4f * sin(PI * clamp01((n - 0.46f) / 0.10f).toDouble()).toFloat() else 0f
    for (i in 0 until 2) {
        val t0 = DROP_T0 + i * DROP_GAP
        if (n < t0) continue
        val u = clamp01((n - t0) / DROP_DUR)
        if (u <= 0f) continue
        val dy = -(1f - easeOutQuint(u)) * DROP_DIST + bob
        val tilt = (1f - easeOutCubic(u)) * LAYER_TILT[i]
        val alpha = clamp01(u / 0.28f) * LAYER_ALPHA[i] * group
        if (alpha <= 0.002f) continue
        withTransform({ translate(0f, dy) }) {
            rotate(tilt, Offset(WORD_CX, WORD_CY)) {
                drawImage(
                    sprite,
                    dstOffset = IntOffset(SPR_X.roundToInt(), SPR_Y.roundToInt()),
                    dstSize = IntSize(SPR_W.roundToInt(), SPR_H.roundToInt()),
                    alpha = alpha,
                    colorFilter = if (i == 0) DIM else null,
                )
            }
        }
    }
}

private fun DrawScope.drawProgress(n: Float, group: Float) {
    val track = clamp01((n - 0.26f) / 0.10f) * group
    if (track <= 0.003f) return
    val x = WORD_CX - BAR_W / 2f
    drawRoundRect(
        Color.White, topLeft = Offset(x, BAR_Y), size = Size(BAR_W, 3f),
        cornerRadius = CornerRadius(1.5f), alpha = 0.10f * track,
    )
    val fill = easeInOutCubic(clamp01((n - 0.38f) / 0.50f))
    if (fill <= 0f) return
    val w = BAR_W * fill
    drawRoundRect(
        Color(0xFFE9A63C), topLeft = Offset(x - 4f, BAR_Y - 3f), size = Size(w + 8f, 9f),
        cornerRadius = CornerRadius(4.5f), alpha = 0.09f * track,
    )
    drawRoundRect(
        Color(0xFFF0B04A), topLeft = Offset(x, BAR_Y), size = Size(w, 3f),
        cornerRadius = CornerRadius(1.5f), alpha = track,
    )
}

/** One band of light crossing the settled word, added on top of it. */
private fun DrawScope.drawSweep(sprite: ImageBitmap, n: Float, group: Float) {
    if (n < 0.56f || n > 0.74f) return
    val p = clamp01((n - 0.56f) / 0.18f)
    val dx = (p - 0.5f) * 1240f
    val fade = sin(PI * p.toDouble()).toFloat() * 0.55f * group
    if (fade <= 0.002f) return
    withTransform({ translate(dx, 0f) }) {
        drawImage(
            sprite,
            dstOffset = IntOffset(SPR_X.roundToInt(), SPR_Y.roundToInt()),
            dstSize = IntSize(SPR_W.roundToInt(), SPR_H.roundToInt()),
            alpha = fade,
            blendMode = BlendMode.Plus,
        )
    }
}

/**
 * Whether this start is a boot, for the piece above to play on.
 *
 * [noteStart] marks the process's first real start. [claim] hands the piece to the first Home that
 * asks within [WINDOW_MS] of it and never again: coming back to Home from an app, or an activity
 * rebuilt under a running process, is not a start and gets no piece.
 */
object BootIntro {

    private const val WINDOW_MS = 20_000L
    private var startedAt = 0L
    private var played = false

    fun noteStart() {
        if (startedAt == 0L) startedAt = SystemClock.uptimeMillis()
    }

    fun claim(): Boolean {
        if (played || startedAt == 0L) return false
        if (SystemClock.uptimeMillis() - startedAt > WINDOW_MS) return false
        played = true
        return true
    }
}
