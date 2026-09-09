package thwiply.elopenmike.com.ui.playground

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import thwiply.elopenmike.com.R
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import thwiply.elopenmike.com.llm.provider.ModelProvider
import thwiply.elopenmike.com.llm.provider.ProviderReadiness
import thwiply.elopenmike.com.ui.main.ProviderForegroundEffect
import thwiply.elopenmike.com.ui.main.label
import thwiply.elopenmike.com.ui.main.message
import thwiply.elopenmike.com.ui.main.AppTopBar
import thwiply.elopenmike.com.ui.main.LocalCompactHeight
import androidx.compose.ui.platform.testTag

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlaygroundScreen(
    onModelSetup: () -> Unit,
    viewModel: PlaygroundViewModel = hiltViewModel()
) {
    val readiness by viewModel.readiness.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val stopped by viewModel.stopped.collectAsStateWithLifecycle()
    val generationFailure by viewModel.generationFailure.collectAsStateWithLifecycle()
    ProviderForegroundEffect(
        viewModel.foreground, selection.provider, viewModel::prepareEngine, viewModel::stop,
    )
    val isInit = readiness == ProviderReadiness.Initializing || (readiness == ProviderReadiness.Checking && busy)
    val isReady = readiness == ProviderReadiness.Ready
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val output by viewModel.output.collectAsStateWithLifecycle()
    val metrics by viewModel.metrics.collectAsStateWithLifecycle()

    var prompt by rememberSaveable { mutableStateOf("Don't forget to review the pull request before our 3pm team meeting!") }
    var isJsonMode by rememberSaveable { mutableStateOf(true) }

    val clipboardManager = LocalClipboardManager.current
    val compactHeight = LocalCompactHeight.current

    val presets = listOf(
        "💬 WhatsApp" to "Hey! Can you bring the HDMI cable and projector to the conference room by 2pm?",
        "💼 Slack Action" to "@channel please submit your Q3 OKR status updates before tomorrow noon.",
        "✈️ Flight Alert" to "United Flight 1902 departs at 6:45 AM from Gate 22. Boarding starts at 6:05 AM.",
        "🛒 Grocery List" to "Don't forget to grab eggs, oat milk, and avocados on your way home."
    )

    Scaffold(
        topBar = {
            if (!compactHeight) PlaygroundHeader()
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .testTag("lab-scroll")
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (compactHeight) PlaygroundHeader()
            LabReadinessCard(readiness, selection.provider, busy, onModelSetup, viewModel::prepareEngine)
            selection.provider?.let {
                Text(stringResource(R.string.provider_selected, stringResource(it.label())))
            }
            if (selection.provider == ModelProvider.GEMINI_NANO) {
                Text(stringResource(R.string.provider_nano_foreground), style = MaterialTheme.typography.bodySmall)
            }
            Text(stringResource(R.string.lab_experimental), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onModelSetup) { Text(stringResource(R.string.provider_select)) }
            generationFailure?.let {
                Text(stringResource(it.kind.message()), color = MaterialTheme.colorScheme.error)
            }
            if (stopped) {
                Text(stringResource(R.string.lab_stopped))
            }
            if (busy && !isGenerating && !isInit) {
                Text(stringResource(R.string.provider_busy))
            }
            // Preset Chips Row
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Sample Inputs",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { (label, sample) ->
                        SuggestionChip(
                            onClick = { prompt = sample },
                            enabled = !busy,
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // Prompt Input Card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it.take(PlaygroundViewModel.MAX_INPUT_CHARACTERS) },
                        label = { Text(stringResource(R.string.lab_input_label)) },
                        supportingText = { Text(stringResource(R.string.lab_input_limit)) },
                        maxLines = 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 90.dp),
                        shape = RoundedCornerShape(14.dp),
                        enabled = !busy && !isGenerating && isReady
                    )

                    // Mode Toggle & Actions Row
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Checkbox(
                                checked = isJsonMode,
                                onCheckedChange = { isJsonMode = it },
                                enabled = !busy && !isGenerating
                            )
                            Text(
                                text = stringResource(R.string.lab_json_mode),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Button(
                            onClick = { viewModel.generate(prompt, isJsonMode) },
                            enabled = !busy && !isGenerating && isReady && prompt.isNotBlank(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            if (isGenerating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.lab_generating))
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.lab_run), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    if (isGenerating) {
                        OutlinedButton(onClick = viewModel::stop) {
                            Text(stringResource(R.string.action_stop))
                        }
                    }
                }
            }

            // Live Performance Metrics Banner
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MetricItem(
                        label = stringResource(R.string.lab_speed),
                        value = if (metrics.charactersPerSec > 0)
                            stringResource(R.string.lab_characters_per_second, metrics.charactersPerSec) else "--"
                    )
                    MetricItem(
                        label = stringResource(R.string.lab_characters),
                        value = metrics.characterCount.toString()
                    )
                    MetricItem(
                        label = stringResource(R.string.lab_elapsed),
                        value = stringResource(R.string.lab_elapsed_ms, metrics.elapsedMs)
                    )
                }
            }
            Text(stringResource(R.string.lab_character_note), style = MaterialTheme.typography.bodySmall)

            // Streaming Output Window
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Output Stream",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    if (output.isNotBlank()) {
                        IconButton(
                            onClick = { clipboardManager.setText(AnnotatedString(output)) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = stringResource(R.string.lab_copy_output),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp)
                ) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        if (isInit) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text(
                                    text = stringResource(readiness.message(selection.provider, busy)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else if (output.isBlank() && !isGenerating) {
                            Text(
                                text = stringResource(if (isReady) R.string.lab_ready else R.string.lab_output_unavailable),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        } else {
                            Text(
                                text = output + if (isGenerating) " ▌" else "",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaygroundHeader() {
    AppTopBar {
        Text(
            "AI Playground",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
        )
        Text(
            "Test on-device extraction & generation performance",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MetricItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun LabReadinessCard(
    readiness: ProviderReadiness,
    provider: ModelProvider?,
    busy: Boolean,
    onModelSetup: () -> Unit,
    onRetry: () -> Unit,
) {
    if (readiness == ProviderReadiness.Ready) return
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(readiness.message(provider, busy)))
            if (readiness == ProviderReadiness.Initializing || (readiness == ProviderReadiness.Checking && busy)) {
                CircularProgressIndicator(Modifier.size(24.dp))
            }
            if (provider == ModelProvider.GEMINI_NANO) {
                Button(onClick = onRetry, enabled = !busy) { Text(stringResource(R.string.nano_check)) }
            } else {
                if (readiness == ProviderReadiness.NeedsInitialization || readiness is ProviderReadiness.Failed) {
                    Button(onClick = onRetry, enabled = !busy) { Text(stringResource(R.string.lab_retry)) }
                }
            }
            OutlinedButton(onClick = onModelSetup) { Text(stringResource(R.string.setup_open)) }
            Text(stringResource(R.string.lab_manual_available), style = MaterialTheme.typography.bodySmall)
        }
    }
}
