package app.orange.tv.ui.home

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orange.tv.CcPage
import app.orange.tv.HomeViewModel
import app.orange.tv.Overlay
import app.orange.tv.R
import app.orange.tv.ui.components.liquidGlass
import app.orange.tv.ui.theme.Motion
import app.orange.tv.ui.theme.Palette
import app.orange.tv.ui.theme.Type
import app.orange.tv.update.Updates
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One line at the top of Home when a new release is seen for the first time that day.
 *
 * Without it the only place an update shows is Control Center, which most owners never open — the
 * release would sit unnoticed for months. It is quiet by design: it appears five seconds after
 * Home settles, leaves after eight, and is shown at most once a day.
 *
 * It takes no remote-control focus (`detectTapGestures`, not `clickable`), so pressing arrows is
 * never interrupted; tapping it with a pointer opens the update page. The remote-control way in is
 * Control Center, which the line names.
 */
@Composable
fun UpdateBanner(vm: HomeViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val latest = Updates.available
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(latest) {
        if (latest == null) return@LaunchedEffect
        if (PromptDay.shownToday(context)) return@LaunchedEffect
        delay(5_000)
        // Home can still be busy when the check lands: the boot notice takes this very place for
        // four seconds, and a panel or the screensaver may be up. Marking the day then would spend
        // it on a banner nobody saw, so wait for a quiet moment — and give up quietly if none comes.
        var waited = 0L
        while (vm.overlay != Overlay.None || vm.screensaverVisible || vm.notice != null) {
            if (waited >= QUIET_WAIT_MS) return@LaunchedEffect
            delay(2_000)
            waited += 2_000
        }
        PromptDay.mark(context)
        visible = true
        delay(8_000)
        visible = false
    }

    AnimatedVisibility(
        // The boot notice shares this place at the top of Home; it wins while it is up.
        visible = visible && vm.overlay == Overlay.None && !vm.screensaverVisible && vm.notice == null,
        modifier = modifier,
        enter = fadeIn(tween(200, easing = Motion.EaseOut)) + slideInVertically(tween(320, easing = Motion.EaseOut)) { -it / 2 },
        exit = fadeOut(tween(200, easing = Motion.EaseOut)) + slideOutVertically(tween(240, easing = Motion.EaseOut)) { -it / 3 },
    ) {
        Row(
            Modifier
                .widthIn(max = 640.dp)
                .liquidGlass(22.dp)
                .pointerInput(Unit) {
                    detectTapGestures { vm.overlay = Overlay.ControlCenter(CcPage.Update) }
                }
                .padding(horizontal = 26.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(Palette.Orange),
            )
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                BasicText(
                    stringResource(R.string.update_banner_title, latest.orEmpty()),
                    style = Type.Callout.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.W600,
                        color = Palette.Label,
                    ),
                )
                BasicText(
                    stringResource(R.string.update_banner_body),
                    style = Type.Caption.copy(fontSize = 13.sp, color = Palette.SecondaryLabel),
                )
            }
        }
    }
}

/** How long to wait for Home to fall quiet before dropping the day's one showing. */
private const val QUIET_WAIT_MS = 120_000L

/** The day the banner was last shown, so that it is once a day and not on every return to Home. */
private object PromptDay {
    private const val PREFS = "update_prompt"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun today(): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    fun shownToday(context: Context): Boolean = prefs(context).getString("day", null) == today()

    fun mark(context: Context) {
        prefs(context).edit().putString("day", today()).apply()
    }
}
