package thwiply.elopenmike.com.ui.preferences

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import thwiply.elopenmike.com.R
import thwiply.elopenmike.com.data.preferences.PreferenceFailureReason
import thwiply.elopenmike.com.data.preferences.PreferenceState

@Composable
fun PreferenceStatus(state: PreferenceState, onRetryRead: () -> Unit) {
    val failure = state.failure
    if (failure != null) {
        Column {
            Text(
                stringResource(when (failure.reason) {
                    PreferenceFailureReason.READ -> R.string.preferences_read_failed
                    PreferenceFailureReason.MALFORMED -> R.string.preferences_malformed
                    PreferenceFailureReason.UNSUPPORTED -> R.string.preferences_unsupported
                    PreferenceFailureReason.WRITE -> R.string.preferences_write_failed
                    PreferenceFailureReason.RESET -> R.string.preferences_reset_failed
                }),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (failure.reason != PreferenceFailureReason.WRITE) {
                TextButton(onClick = onRetryRead) { Text(stringResource(R.string.preferences_retry)) }
            }
        }
    } else if (state.values == null) {
        Text(stringResource(R.string.preferences_loading))
    }
}
