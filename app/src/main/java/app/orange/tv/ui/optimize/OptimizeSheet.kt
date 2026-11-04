package app.orange.tv.ui.optimize

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orange.tv.R
import app.orange.tv.ui.components.Pressable
import app.orange.tv.ui.components.consumeTaps
import app.orange.tv.ui.components.dismissOnTap
import app.orange.tv.ui.components.liquidGlass
import app.orange.tv.ui.theme.Motion
import app.orange.tv.ui.theme.Palette
import app.orange.tv.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * One-tap clean-up, reached from Control Center. Three things at once: what is already cleaned,
 * what a tap would reclaim, and a button that actually does it — no invented "3.2 GB of junk".
 */
@Composable
fun OptimizeSheet(visible: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var snap by remember { mutableStateOf(OptimizeSnapshot()) }
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<OptimizeResult?>(null) }
    val button = remember { FocusRequester() }

    BackHandler(enabled = visible) { if (!running) onDismiss() }

    LaunchedEffect(visible) {
        if (!visible) {
            result = null
            return@LaunchedEffect
        }
        snap = Optimizer.snapshot(context)
        withFrameNanos { }
        runCatching { button.requestFocus() }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200, easing = Motion.EaseOut)) + scaleIn(tween(300, easing = Motion.Drawer), initialScale = 0.94f),
        exit = fadeOut(tween(150, easing = Motion.EaseOut)) + scaleOut(tween(150, easing = Motion.EaseOut), targetScale = 0.97f),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            Modifier.fillMaxSize()
                .dismissOnTap { if (!running) onDismiss() }
                .background(Color.Black.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .width(560.dp)
                    .consumeTaps()
                    .liquidGlass(26.dp)
                    .padding(horizontal = 30.dp, vertical = 26.dp),
            ) {
                BasicText(stringResource(R.string.opt_title), style = Type.LargeTitle)
                BasicText(
                    stringResource(R.string.opt_subtitle),
                    style = Type.Caption.copy(color = Palette.TertiaryLabel),
                    modifier = Modifier.padding(top = 4.dp),
                )

                Spacer(Modifier.height(22.dp))

                // The meter: what is free, out of how much, and how full that makes the TV look.
                Row(verticalAlignment = Alignment.Bottom) {
                    BasicText(
                        "${snap.availMb}",
                        style = Type.LargeTitle.copy(fontSize = 38.sp, color = Palette.Green),
                    )
                    BasicText(
                        " MB",
                        style = Type.Callout.copy(color = Palette.SecondaryLabel),
                        modifier = Modifier.padding(bottom = 6.dp, start = 3.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    BasicText(
                        stringResource(R.string.opt_total, gb(snap.totalMb)),
                        style = Type.Caption.copy(color = Palette.SecondaryLabel),
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }

                Spacer(Modifier.height(9.dp))
                val used = if (snap.totalMb > 0) {
                    ((snap.totalMb - snap.availMb).toFloat() / snap.totalMb).coerceIn(0f, 1f)
                } else 0f
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Palette.Fill)) {
                    Box(Modifier.fillMaxWidth(used).fillMaxHeight().background(Palette.Orange))
                }

                Spacer(Modifier.height(20.dp))

                // Three numbers, in the order they matter: cleaned already, cleanable now, neglected.
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat(snap.purified.toString(), stringResource(R.string.opt_purified), Modifier.weight(1f))
                    Stat("${snap.cacheMb} MB", stringResource(R.string.opt_cache), Modifier.weight(1f))
                    Stat(snap.idleApps.toString(), stringResource(R.string.opt_idle), Modifier.weight(1f))
                }

                Spacer(Modifier.height(22.dp))

                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Pressable(
                        onClick = {
                            if (running) return@Pressable
                            running = true
                            scope.launch {
                                val r = Optimizer.run(context)
                                snap = Optimizer.snapshot(context)
                                result = r
                                running = false
                            }
                        },
                        focusRequester = button,
                        enabled = !running,
                        focusedScale = 1.04f,
                        // The four arrows stop on the button instead of looking for focus behind the sheet.
                        onKey = {
                            it.key == Key.DirectionUp || it.key == Key.DirectionDown ||
                                it.key == Key.DirectionLeft || it.key == Key.DirectionRight
                        },
                    ) { state ->
                        val bg = if (state.focused) Palette.Focus else Palette.FillStrong
                        val fg = if (state.focused) Palette.OnFocus else Palette.Label
                        Box(
                            Modifier.clip(RoundedCornerShape(50)).background(bg).height(46.dp)
                                .padding(horizontal = 34.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(
                                stringResource(if (running) R.string.opt_running else R.string.opt_run),
                                style = Type.Body.copy(color = fg),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                val note = result?.let { r ->
                    when {
                        r.freedMb > 0 && r.cacheClearedMb > 0 ->
                            stringResource(R.string.opt_freed_both, r.freedMb, r.cacheClearedMb)
                        r.freedMb > 0 -> stringResource(R.string.opt_freed, r.freedMb)
                        r.cacheClearedMb > 0 -> stringResource(R.string.opt_cache_done, r.cacheClearedMb)
                        else -> stringResource(R.string.opt_nothing)
                    }
                }
                if (note != null) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        BasicText(
                            note,
                            style = Type.Caption.copy(color = Palette.Green),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(Palette.Fill).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(value, style = Type.Title.copy(fontSize = 22.sp))
        BasicText(
            label,
            style = Type.Caption.copy(fontSize = 11.sp, color = Palette.TertiaryLabel),
            modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
        )
    }
}

private fun gb(mb: Long): String =
    if (mb >= 1024) String.format("%.1f GB", mb / 1024f) else "$mb MB"
