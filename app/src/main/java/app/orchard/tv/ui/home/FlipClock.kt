package app.orchard.tv.ui.home

import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orchard.tv.settings.AerialSettings
import app.orchard.tv.ui.theme.LocalReducedMotion
import app.orchard.tv.ui.theme.Motion
import app.orchard.tv.ui.theme.Type
import java.time.LocalDateTime
import kotlin.random.Random

/**
 * The flip clock screensaver: the time as four cards that turn over, a pair for the hour and a
 * pair for the minute, on black. A card turns only when its own digit changes, so between turns
 * the screen is still — the TV is left on all night, and a second hand turning something over
 * every second is both motion a resting screen should not have (AGENTS.md) and a stutter on the
 * panels that cannot draw it (Xiaomi MiTV-MFTR0, 8-10 frames a second).
 *
 * Hours and minutes only, for that reason. The block also moves a little every minute, so nothing
 * sits on the same pixels all night (OLED panels, issue #416) — the same reason the small clock on
 * the aerial films drifts ([saverDrift]). Personalize's anti-burn-in switch off, or the TV's own
 * "remove animations" on, and the cards stand still in the middle.
 *
 * The turn is the classic one: the top half of the old digit falls away, hinged on the seam, and
 * the bottom half of the new one falls into place behind it, in [FlipTurnMs]. Both halves are the
 * same digit drawn twice and clipped, not two pictures.
 */
@Composable
internal fun FlipClockSaver(now: LocalDateTime) {
    val context = LocalContext.current
    val is24 = remember(context) { DateFormat.is24HourFormat(context) }
    val hour = two(if (is24) now.hour else (now.hour % 12).let { if (it == 0) 12 else it })
    val minute = two(now.minute)

    // Somewhere new every minute, and nowhere near the edges: a whole minute of drift is one
    // 2.4 s move, too slow to read as movement, and a card's turn lasts 0.6 s, so the two never
    // fight for the same frames. Nothing at all with anti-burn-in off or the TV's animations off.
    val minuteTick = now.withSecond(0).withNano(0)
    val still = LocalReducedMotion.current || !AerialSettings.burnIn
    val wander = remember(minuteTick, still) {
        if (still) {
            DpOffset.Zero
        } else {
            val rnd = Random(minuteTick.hashCode())
            DpOffset(FlipDriftX * (rnd.nextFloat() * 2 - 1), FlipDriftY * (rnd.nextFloat() * 2 - 1))
        }
    }
    val wanderX by animateDpAsState(
        wander.x, if (still) snap() else tween(FlipDriftMs, easing = Motion.EaseInOut), label = "flip-x",
    )
    val wanderY by animateDpAsState(
        wander.y, if (still) snap() else tween(FlipDriftMs, easing = Motion.EaseInOut), label = "flip-y",
    )

    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Row(
            Modifier.offset(x = wanderX, y = wanderY),
            horizontalArrangement = Arrangement.spacedBy(FlipCardGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlipCard(hour[0].toString())
            FlipCard(hour[1].toString())
            Seam()
            FlipCard(minute[0].toString())
            FlipCard(minute[1].toString())
        }
    }
}

/**
 * One card: the digit it is showing, and the turn from the one before it.
 *
 * The turn's progress is read in three places — the layer of each leaf (which only redraws), and
 * the two halves' digits (which recompose when the progress crosses half way and the end). Nothing
 * recomposes per frame, which matters on a TV that drops to single-digit frames.
 */
@Composable
private fun FlipCard(digit: String) {
    var resting by remember { mutableStateOf(digit) }
    var leaving by remember { mutableStateOf(digit) }
    val turn = remember { Animatable(1f) }
    val reduced = LocalReducedMotion.current

    LaunchedEffect(digit) {
        if (digit == resting) return@LaunchedEffect
        leaving = resting
        resting = digit
        if (reduced) {
            turn.snapTo(1f)
        } else {
            turn.snapTo(0f)
            turn.animateTo(1f, tween(FlipTurnMs, easing = LinearEasing))
        }
    }

    // Past the half turn the upper leaf is gone and the new digit shows behind it; past the whole
    // turn the lower leaf has landed and the old digit is gone for good.
    val halfTurned by remember { derivedStateOf { turn.value >= 0.5f } }
    val landed by remember { derivedStateOf { turn.value >= 1f } }

    Box(Modifier.size(FlipCardWidth, FlipCardHeight)) {
        Column {
            Box(
                Modifier.size(FlipCardWidth, FlipCardHeight / 2)
                    .clip(RoundedCornerShape(topStart = FlipCardCorner, topEnd = FlipCardCorner))
                    .background(FlipCardFill),
            ) {
                HalfDigit(if (halfTurned) resting else leaving, top = true)
                if (!halfTurned) {
                    // The leaf falling away: hinged on the seam, so it turns into the space the
                    // lower half takes up, not into the card above it.
                    Box(
                        Modifier.fillMaxSize()
                            .graphicsLayer {
                                transformOrigin = TransformOrigin(0.5f, 1f)
                                rotationX = -90f * (turn.value / 0.5f)
                                cameraDistance = FlipCamera * density
                            }
                            .background(FlipCardFill),
                    ) {
                        HalfDigit(leaving, top = true)
                        // Its face leaves the light as it turns.
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f * (turn.value / 0.5f))))
                    }
                }
            }
            Box(
                Modifier.size(FlipCardWidth, FlipCardHeight / 2)
                    .clip(RoundedCornerShape(bottomStart = FlipCardCorner, bottomEnd = FlipCardCorner))
                    .background(FlipCardFill),
            ) {
                HalfDigit(if (landed) resting else leaving, top = false)
                if (halfTurned && !landed) {
                    // The leaf falling into place, hinged on the seam from the other side.
                    Box(
                        Modifier.fillMaxSize()
                            .graphicsLayer {
                                transformOrigin = TransformOrigin(0.5f, 0f)
                                rotationX = 90f * (1f - (turn.value - 0.5f) / 0.5f)
                                cameraDistance = FlipCamera * density
                            }
                            .background(FlipCardFill),
                    ) {
                        HalfDigit(resting, top = false)
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f * (1f - (turn.value - 0.5f) / 0.5f))))
                    }
                }
            }
        }
        // The seam: the two halves are two plates, and this line is what says so.
        Box(Modifier.align(Alignment.Center).fillMaxWidth().height(2.dp).background(Color.Black))
    }
}

/**
 * One half of a digit, the whole digit drawn in a half-height window: [top] shows its upper half,
 * else its lower. Clipped rather than measured so both halves keep the digit's own centre, and
 * the two line up on the seam exactly.
 */
@Composable
private fun HalfDigit(digit: String, top: Boolean) {
    // Measured with the whole card's height and then placed so that only the wanted half falls in
    // the window. A Box would do the placing for us, but anything laid out inside a half-height
    // parent is measured against that half (size, and even requiredSize once the parent's own
    // constraints reach it), which draws a whole digit in each half instead of a cropped one.
    Layout(
        content = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                BasicText(digit, style = FlipDigitStyle)
            }
        },
        modifier = Modifier.fillMaxSize().clipToBounds(),
    ) { measurables, constraints ->
        val window = constraints.maxWidth
        val half = constraints.maxHeight
        val card = measurables.first().measure(Constraints.fixed(window, half * 2))
        layout(window, half) { card.place(0, if (top) 0 else -half) }
    }
}

/** The colon between the pairs: two dots, never blinking, since nothing else on the screen does. */
@Composable
private fun Seam() {
    Column(
        Modifier.width(FlipSeamWidth),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        repeat(2) {
            Box(Modifier.size(12.dp).clip(RoundedCornerShape(50)).background(FlipDigitColor))
        }
    }
}

private fun two(value: Int) = if (value < 10) "0$value" else value.toString()

// The screensaver's canvas is 960x540 dp (MainActivity scales it): four cards, three gaps and the
// colon come to 700 dp, and the block sits in the middle of the height.
private val FlipCardWidth = 150.dp
private val FlipCardHeight = 204.dp
private val FlipCardGap = 14.dp
private val FlipSeamWidth = 26.dp
private val FlipCardCorner = 16.dp

/** A little darker than the black around it: the plates read as plates, the digits as light. */
private val FlipCardFill = Color(0xFF141416)
private val FlipDigitColor = Color(0xFFF2F2F4)

/** Fast enough that the clock is never seen mid-turn for long, slow enough to read as a turn. */
private const val FlipTurnMs = 620

/** 2.4 s, as the screensaver's other moves (ClockSaver's scene, the labels' fade). */
private const val FlipDriftMs = 2400

/** Far enough to keep the plates off one another's pixels, small enough not to look restless. */
private val FlipDriftX = 40.dp
private val FlipDriftY = 26.dp

/** The perspective of a turn: too flat and it reads as a wipe, too deep and the plates bend. */
private const val FlipCamera = 20f

private val FlipDigitStyle = Type.Clock.copy(fontSize = 150.sp, letterSpacing = 0.sp, color = FlipDigitColor)
