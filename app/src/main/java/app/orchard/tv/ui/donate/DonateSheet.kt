package app.orchard.tv.ui.donate

import android.content.Context
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orchard.tv.R
import app.orchard.tv.ui.components.consumeTaps
import app.orchard.tv.ui.components.dismissOnTap
import app.orchard.tv.ui.theme.Motion
import app.orchard.tv.ui.theme.Type

/**
 * Who makes Orange, and how to thank them: one glass sheet over Home, reached from Personalization
 * and closed back to it. Nothing here is a control, so the sheet takes the remote's focus itself
 * (its own D-pad keys go nowhere rather than moving Home's focus behind the dimming).
 */
@Composable
fun DonateSheet(visible: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    // Decoded once per visit: three pictures of a few hundred KB each.
    val avatar = remember { context.drawable(R.drawable.donate_avatar) }
    val wechat = remember { context.drawable(R.drawable.donate_wechat) }
    val alipay = remember { context.drawable(R.drawable.donate_alipay) }

    BackHandler(enabled = visible, onBack = onDismiss)
    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        withFrameNanos { }
        runCatching { focus.requestFocus() }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200, easing = Motion.EaseOut)) + scaleIn(tween(300, easing = Motion.Drawer), initialScale = 0.94f),
        exit = fadeOut(tween(150, easing = Motion.EaseOut)) + scaleOut(tween(150, easing = Motion.EaseOut), targetScale = 0.97f),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            Modifier.fillMaxSize().dismissOnTap(onDismiss).background(Color.Black.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    // A small card with a lot of screen left around it: it reads as a note, not a page.
                    .width(528.dp)
                    // A fixed height, so the thanks can sit in the middle of the card rather than
                    // under the name: the codes below take the same room on every TV.
                    .height(360.dp)
                    .consumeTaps()
                    .clip(RoundedCornerShape(23.dp))
                    .background(CardBlue)
                    .padding(horizontal = 36.dp, vertical = 26.dp)
                    .focusRequester(focus)
                    .focusable()
                    // The remote's move and pick keys stop here; volume, Back and the rest pass through.
                    .onPreviewKeyEvent {
                        it.key == Key.DirectionUp || it.key == Key.DirectionDown ||
                            it.key == Key.DirectionLeft || it.key == Key.DirectionRight ||
                            it.key == Key.DirectionCenter || it.key == Key.Enter
                    },
            ) {
                // Who, across the top: the card is about the words, not the name.
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(13.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    avatar?.let {
                        Image(
                            it, contentDescription = null,
                            modifier = Modifier.size(42.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Column {
                        BasicText(
                            stringResource(R.string.donate_author),
                            style = Type.LargeTitle.copy(fontSize = 24.sp, color = Ink),
                        )
                        BasicText(
                            stringResource(R.string.donate_role),
                            style = Type.Caption.copy(fontSize = 12.sp, color = InkSoft),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }

                // The thanks in the middle, and the promise not to hold anything back under it.
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Two sentences, two blocks: the line break lands after the first one instead
                        // of wherever the font metrics happen to fill the width.
                        val thanks = Type.Body.copy(
                            fontSize = 15.sp, lineHeight = 29.sp,
                            color = Ink, textAlign = TextAlign.Center,
                        )
                        BasicText(stringResource(R.string.donate_thanks), style = thanks, modifier = Modifier.widthIn(max = 456.dp))
                        BasicText(stringResource(R.string.donate_thanks_more), style = thanks, modifier = Modifier.widthIn(max = 456.dp))
                        BasicText(
                            stringResource(R.string.donate_note),
                            style = Type.Caption.copy(
                                fontSize = 13.sp, color = InkFaint, textAlign = TextAlign.Center,
                            ),
                            modifier = Modifier.padding(top = 14.dp),
                        )
                    }
                }

                // The codes as one group, centred: the margins either side are the card's own.
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(62.dp, Alignment.CenterHorizontally),
                ) {
                    Code(wechat, R.string.donate_wechat)
                    Code(alipay, R.string.donate_alipay)
                }
            }
        }
    }
}

/** One code on a white plate, its name under it: what a phone needs to see. */
@Composable
private fun Code(bitmap: ImageBitmap?, label: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(107.dp).clip(RoundedCornerShape(13.dp)).background(Color.White).padding(7.dp),
            contentAlignment = Alignment.Center,
        ) {
            bitmap?.let { Image(it, contentDescription = stringResource(label), modifier = Modifier.fillMaxSize()) }
        }
        BasicText(
            stringResource(label),
            style = Type.Caption.copy(fontSize = 16.sp, color = Ink),
            modifier = Modifier.padding(top = 9.dp),
        )
    }
}

// The card is a flat pale blue with near-black ink: a sheet of paper on a dark screen, readable
// from across the room and quiet enough not to compete with the codes.
private val CardBlue = Color(0xFFD3E0EF)
private val Ink = Color(0xFF1F2C3A)
private val InkSoft = Color(0xFF62748A)
private val InkFaint = Color(0xFF7C8D9E)

private fun Context.drawable(id: Int): ImageBitmap? =
    runCatching { BitmapFactory.decodeResource(resources, id)?.asImageBitmap() }.getOrNull()
