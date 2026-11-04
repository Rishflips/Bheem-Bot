package app.bheem.app

import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.bheem.core.assistant.Waiting
import app.bheem.models.host.ModelState
import kotlinx.coroutines.delay

/** Above the input while the model loads: says how long it usually takes and fills as time passes. Never a card at the top of the thread. */
@Composable
fun LoadStrip(model: ModelState, lastLoadSeconds: Long?, busy: Boolean, onCancel: () -> Unit, onRetry: () -> Unit) {
    when (model) {
        ModelState.Verifying, is ModelState.Loading -> {
            var elapsed by remember { mutableLongStateOf(0L) }
            LaunchedEffect(Unit) {
                val started = SystemClock.elapsedRealtime()
                while (true) {
                    elapsed = SystemClock.elapsedRealtime() - started
                    delay(250)
                }
            }
            val text = when (model) {
                ModelState.Verifying -> stringResource(R.string.model_verifying)
                else -> lastLoadSeconds?.let { stringResource(R.string.model_loading_seconds, it.toInt()) } ?: stringResource(R.string.model_loading)
            }
            val progress = Waiting.loadProgress(elapsed, lastLoadSeconds)
            Column(Modifier.fillMaxWidth().padding(top = 4.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    if (busy) TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                }
                if (progress != null) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        is ModelState.Failed -> Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(model.reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
            TextButton(onClick = onRetry) { Text(stringResource(R.string.try_again)) }
        }
        else -> Unit
    }
}

/** Where the answer will appear: breathing dots, a second line after six seconds, and Stop at the top so it does not move as text grows. */
@Composable
fun ThinkingBubble(partial: String?, onStop: () -> Unit) {
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val started = SystemClock.elapsedRealtime()
        while (true) {
            elapsed = SystemClock.elapsedRealtime() - started
            delay(500)
        }
    }
    val thinking = stringResource(R.string.thinking_description)
    val streaming = !partial.isNullOrEmpty()
    Column(Modifier.fillMaxWidth().padding(end = 24.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
        if (!streaming) {
            Text(stringResource(R.string.turn_bheem), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
        }
        Surface(
            color = if (streaming) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
            border = if (streaming) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
            shape = BheemBubbleShape(false),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (streaming) {
                        SmallIcon(R.drawable.ic_sparkle, MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.turn_bheem_model), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onStop) { Text(stringResource(R.string.chat_stop)) }
                }
                if (streaming) {
                    Text(partial.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                } else {
                    ThinkingDots(Modifier.semantics { contentDescription = thinking })
                }
                if (Waiting.showStillOnIt(elapsed)) {
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.wait_still_on_it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ThinkingDots(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val animatorScale = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }
    val still = Waiting.staticDots(animatorScale, LocalConfiguration.current.fontScale)
    val alpha = if (still) 1f else {
        val transition = rememberInfiniteTransition(label = "thinking")
        transition.animateFloat(0.3f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "alpha").value
    }
    Row(modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { Box(Modifier.size(8.dp).alpha(alpha).background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)) }
    }
}

@Composable
fun SmallIcon(id: Int, tint: Color, modifier: Modifier = Modifier) {
    Icon(painterResource(id), contentDescription = null, tint = tint, modifier = modifier.size(18.dp))
}
