package app.bheem.app

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.bheem.core.speech.VoiceState

/** A pill field and a round Talk button that becomes Send once there is text. Hold to talk is unchanged. */
@Composable
fun InputBar(
    text: String,
    onTextChange: (String) -> Unit,
    busy: Boolean,
    voice: VoiceState,
    onSend: (String) -> Unit,
    onStartTalk: () -> Unit,
    onStopTalk: () -> Unit,
) {
    val listening = voice as? VoiceState.Listening
    val talkDescription = stringResource(R.string.talk_description)
    val listeningState = stringResource(R.string.listening_state)
    val toggleLabel = stringResource(R.string.talk_toggle)
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        if (listening != null) {
            Text(stringResource(R.string.listening), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            LinearProgressIndicator(progress = { listening.level }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.extraLarge,
                placeholder = { Text(stringResource(if (busy) R.string.bar_thinking else R.string.bar_hint)) },
                enabled = !busy && listening == null,
                singleLine = true,
            )
            if (text.isBlank()) {
                Surface(
                    color = if (listening != null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    contentColor = if (listening != null) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(56.dp)
                        .clearAndSetSemantics {
                            role = Role.Button
                            contentDescription = talkDescription
                            if (listening != null) stateDescription = listeningState
                            onClick(label = toggleLabel) {
                                if (listening != null) onStopTalk() else onStartTalk()
                                true
                            }
                        }
                        .pointerInput(busy) {
                            if (!busy) {
                                detectTapGestures(onPress = {
                                    onStartTalk()
                                    tryAwaitRelease()
                                    onStopTalk()
                                })
                            }
                        },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_mic), contentDescription = null, modifier = Modifier.size(24.dp))
                    }
                }
            } else {
                FilledIconButton(onClick = { onSend(text) }, enabled = !busy, modifier = Modifier.size(56.dp)) {
                    Icon(painterResource(R.drawable.ic_send), contentDescription = stringResource(R.string.send), modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}
