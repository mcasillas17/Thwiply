package thwiply.elopenmike.com.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.ui.res.painterResource
import thwiply.elopenmike.com.R
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
import androidx.hilt.navigation.compose.hiltViewModel
import thwiply.elopenmike.com.llm.model.DownloadState
import thwiply.elopenmike.com.llm.model.ModelLoadState
import thwiply.elopenmike.com.llm.provider.ModelProvider
import thwiply.elopenmike.com.llm.provider.NanoState
import thwiply.elopenmike.com.llm.provider.ProviderSelection
import thwiply.elopenmike.com.ui.main.ProviderForegroundEffect
import thwiply.elopenmike.com.ui.main.label
import thwiply.elopenmike.com.ui.main.message
import thwiply.elopenmike.com.ui.theme.ElectricCyanAccent
import thwiply.elopenmike.com.data.preferences.AppPreferences
import thwiply.elopenmike.com.data.preferences.PreferenceState
import thwiply.elopenmike.com.ui.preferences.PreferenceStatus

@Composable
fun OnboardingScreen(
    onExit: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val selection by viewModel.selection.collectAsState()
    val nanoState by viewModel.nanoState.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val selecting by viewModel.selecting.collectAsState()
    val failure by viewModel.failure.collectAsState()
    val qwenLoadState by viewModel.qwenLoadState.collectAsState()
    val preferences by viewModel.preferences.collectAsState()
    ProviderForegroundEffect(
        viewModel.foreground, selection.provider to selecting,
        onEnter = {
            if (!selecting && selection.provider == ModelProvider.GEMINI_NANO) viewModel.checkNano()
        },
        onExit = viewModel::pauseDownload,
    )
    OnboardingContent(
        state, selection, nanoState, busy || selecting,
        viewModel::selectProvider, viewModel::startDownload,
        viewModel::checkNano, viewModel::downloadNano,
        viewModel::pauseDownload, onExit,
        failure?.kind?.message(),
        qwenLoadState,
        preferences,
        viewModel::acknowledgeModelSetupEducation,
        viewModel::reloadPreferences,
    )
}

@Composable
fun OnboardingContent(
    state: DownloadState,
    selection: ProviderSelection,
    nanoState: NanoState,
    busy: Boolean,
    onSelectProvider: (ModelProvider) -> Unit,
    onStartDownload: () -> Unit,
    onCheckNano: () -> Unit,
    onDownloadNano: () -> Unit,
    onStop: () -> Unit,
    onExit: () -> Unit,
    failureMessage: Int?,
    qwenLoadState: ModelLoadState = ModelLoadState.Loaded,
    preferences: PreferenceState = PreferenceState(AppPreferences()),
    onAcknowledgeEducation: () -> Unit = {},
    onRetryPreferences: () -> Unit = {},
) {
    BackHandler(onBack = onExit)

    val isDownloading = state is DownloadState.Downloading
    val controlsEnabled = !busy && !isDownloading
    var confirmNanoDownload by remember { mutableStateOf(false) }
    var confirmNanoUse by remember { mutableStateOf(false) }
    var expandEducation by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onExit) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.setup_back))
                }
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(ElectricCyanAccent)
                )
                Text(
                    text = "THWIPLY",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            // Hero Banner Section
            HeroBanner()
            PreferenceStatus(preferences, onRetryPreferences)
            if (preferences.values?.hasSeenModelSetupEducation != true || expandEducation) {
                Column {
                    Text(
                        stringResource(R.string.setup_optional_description),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(
                        enabled = preferences.canUpdate,
                        onClick = { expandEducation = false; onAcknowledgeEducation() },
                    ) { Text(stringResource(R.string.education_acknowledge)) }
                }
            } else {
                TextButton(onClick = { expandEducation = true }) {
                    Text(stringResource(R.string.education_show))
                }
            }

            // Value Proposition Pills
            ValuePropsRow()

            // Model Selection Section
            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.provider_select),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                ModelProvider.entries.forEach { provider ->
                    ModelSelectionCard(
                        provider = provider,
                        isSelected = selection.provider == provider,
                        enabled = controlsEnabled,
                        onClick = {
                            if (provider == ModelProvider.GEMINI_NANO && selection.provider != provider) {
                                confirmNanoUse = true
                            } else {
                                onSelectProvider(provider)
                            }
                        },
                    )
                }
            }
            if (selection.failure != null) {
                Text(stringResource(R.string.provider_preference_failed), color = MaterialTheme.colorScheme.error)
            } else if (selection.provider == null) {
                Text(stringResource(R.string.provider_loading))
            }
            failureMessage?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            if (selection.provider == ModelProvider.QWEN) {
                when (qwenLoadState) {
                    ModelLoadState.Loading -> Text(stringResource(R.string.qwen_metadata_checking))
                    is ModelLoadState.Failed -> Text(
                        stringResource(R.string.qwen_metadata_failed), color = MaterialTheme.colorScheme.error,
                    )
                    ModelLoadState.Loaded -> Unit
                }
            }

            AnimatedVisibility(visible = selection.provider == ModelProvider.QWEN && state !is DownloadState.Idle) {
                DownloadStatusCard(state = state)
            }
            if (selection.provider == ModelProvider.GEMINI_NANO) {
                Text(stringResource(R.string.provider_nano_foreground), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(if (nanoState == NanoState.Checking && !busy)
                    R.string.nano_check_needed else nanoState.message()))
                if ((nanoState == NanoState.Checking && busy) || nanoState == NanoState.Downloading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                OutlinedButton(onClick = onCheckNano, enabled = controlsEnabled) {
                    Text(stringResource(R.string.nano_check))
                }
                if (nanoState == NanoState.Downloadable) {
                    Button(
                        onClick = { confirmNanoDownload = true },
                        enabled = controlsEnabled,
                    ) { Text(stringResource(R.string.nano_prepare)) }
                }
            }
            if (busy || isDownloading) {
                Text(stringResource(R.string.provider_busy), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = onStop) { Text(stringResource(R.string.action_stop)) }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Primary Call to Action Button
            if (selection.provider == ModelProvider.QWEN) Button(
                onClick = { if (state is DownloadState.Success) onExit() else onStartDownload() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .shadow(
                        elevation = if (isDownloading) 0.dp else 8.dp,
                        shape = RoundedCornerShape(16.dp),
                        ambientColor = ElectricCyanAccent,
                        spotColor = ElectricCyanAccent
                    ),
                enabled = controlsEnabled && qwenLoadState != ModelLoadState.Loading,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.setup_downloading),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(when (state) {
                                is DownloadState.Error -> R.string.setup_retry
                                DownloadState.Success -> R.string.setup_return
                                else -> R.string.setup_download
                            }),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            if (selection.provider != ModelProvider.QWEN) {
                OutlinedButton(onClick = onExit) { Text(stringResource(R.string.setup_return)) }
            }
            if (confirmNanoUse) {
                AlertDialog(
                    onDismissRequest = { confirmNanoUse = false },
                    title = { Text(stringResource(R.string.nano_use_title)) },
                    text = { Text(stringResource(R.string.nano_use_consent)) },
                    confirmButton = {
                        TextButton(
                            enabled = controlsEnabled,
                            onClick = { confirmNanoUse = false; onSelectProvider(ModelProvider.GEMINI_NANO) },
                        ) { Text(stringResource(R.string.nano_use_confirm)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmNanoUse = false }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    },
                )
            }
            if (confirmNanoDownload) {
                AlertDialog(
                    onDismissRequest = { confirmNanoDownload = false },
                    title = { Text(stringResource(R.string.nano_consent_title)) },
                    text = { Text(stringResource(R.string.nano_consent_body)) },
                    confirmButton = {
                        TextButton(
                            enabled = controlsEnabled && selection.provider == ModelProvider.GEMINI_NANO,
                            onClick = { confirmNanoDownload = false; onDownloadNano() },
                        ) { Text(stringResource(R.string.nano_consent_confirm)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmNanoDownload = false }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    },
                )
            }
        }
    }
}


@Composable
private fun HeroBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Glowing Spider Web Vector Badge
            GlowingSpiderWebIcon()

            Text(
                text = "Thwiply",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = stringResource(R.string.setup_hero),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GlowingSpiderWebIcon(
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    Box(
        modifier = modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF002F6E),
                        Color(0xFF001A3D)
                    )
                )
            )
            .border(2.dp, accentColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(58.dp)
        )
    }
}

@Composable
private fun ValuePropsRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ValuePropBadge(
            icon = Icons.Default.Security,
            label = stringResource(R.string.setup_local_ai),
            modifier = Modifier.weight(1f)
        )
        ValuePropBadge(
            icon = Icons.Default.Bolt,
            label = stringResource(R.string.setup_explicit_choice),
            modifier = Modifier.weight(1f)
        )
        ValuePropBadge(
            icon = Icons.Default.TaskAlt,
            label = stringResource(R.string.setup_alpha_lab),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ValuePropBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ModelSelectionCard(
    provider: ModelProvider,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = isSelected, enabled = enabled,
                role = Role.RadioButton, onClick = onClick,
            )
            .animateContentSize(),
        shape = RoundedCornerShape(18.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        },
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = stringResource(provider.label()),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                    )
                }
            }

            Text(
                stringResource(if (provider == ModelProvider.QWEN)
                    R.string.provider_qwen_description else R.string.provider_nano_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DownloadStatusCard(state: DownloadState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = when (state) {
            is DownloadState.Error -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
            is DownloadState.Success -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        },
        border = BorderStroke(
            1.dp,
            when (state) {
                is DownloadState.Error -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (state) {
                is DownloadState.Downloading -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.setup_downloading),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${state.progress}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = ElectricCyanAccent,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
                is DownloadState.Error -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = stringResource(R.string.setup_error),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                is DownloadState.Success -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.setup_complete),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                else -> {}
            }
        }
    }
}
