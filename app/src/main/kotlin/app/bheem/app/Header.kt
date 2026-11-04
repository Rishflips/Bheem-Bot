package app.bheem.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** The wordmark, one quiet line that says where the data is, and the settings button. */
@Composable
fun Header(status: String?, onOpenSettings: () -> Unit) {
    val roomForStatus = LocalConfiguration.current.fontScale < 1.5f
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.today_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        if (roomForStatus && status != null) {
            Text(status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).padding(top = 2.dp))
        } else {
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        }
        IconButton(onClick = onOpenSettings, modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)) {
            Image(
                painterResource(R.drawable.ic_settings),
                contentDescription = stringResource(R.string.settings_button),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
            )
        }
    }
}
