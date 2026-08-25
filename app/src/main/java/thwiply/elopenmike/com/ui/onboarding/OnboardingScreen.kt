package thwiply.elopenmike.com.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import thwiply.elopenmike.com.llm.model.DownloadState
import thwiply.elopenmike.com.llm.model.ModelPreset
import thwiply.elopenmike.com.ui.theme.ElectricCyanAccent
import thwiply.elopenmike.com.ui.theme.ThemeMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun OnboardingScreen(
    onDownloadComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val selectedPreset by viewModel.selectedPreset.collectAsState()
    val customUrl by viewModel.customUrl.collectAsState()
    val hfToken by viewModel.hfToken.collectAsState()

    val uriHandler = LocalUriHandler.current
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(state) {
        if (state is DownloadState.Success) {
            onDownloadComplete()
        }
    }

    val isDownloading = state is DownloadState.Downloading

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

            // Value Proposition Pills
            ValuePropsRow()

            // Model Selection Section
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Select On-Device Engine",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                // Qwen 2.5 1.5B Card (Default • 1-Click)
                ModelSelectionCard(
                    preset = ModelPreset.QWEN_2_5_1_5B,
                    isSelected = selectedPreset.id == ModelPreset.QWEN_2_5_1_5B.id,
                    badgeColor = MaterialTheme.colorScheme.primary,
                    badgeTextColor = MaterialTheme.colorScheme.onPrimary,
                    onClick = { if (!isDownloading) viewModel.selectPreset(ModelPreset.QWEN_2_5_1_5B) }
                ) {
                    Text(
                        text = "⚡ Instant download with zero accounts or tokens. Exceptional structured task extraction.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Gemma 3 1B Card (Google AI Edge • Gated)
                ModelSelectionCard(
                    preset = ModelPreset.GEMMA_3_1B,
                    isSelected = selectedPreset.id == ModelPreset.GEMMA_3_1B.id,
                    badgeColor = MaterialTheme.colorScheme.secondaryContainer,
                    badgeTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = { if (!isDownloading) viewModel.selectPreset(ModelPreset.GEMMA_3_1B) }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Ultra-compact Google model (~550 MB). Requires accepting license terms on Hugging Face.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Expandable Token Setup when selected
                        AnimatedVisibility(visible = selectedPreset.id == ModelPreset.GEMMA_3_1B.id) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Step Buttons Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { uriHandler.openUri("https://huggingface.co/google/gemma-3-1b-it") },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("1. Accept License ↗", style = MaterialTheme.typography.labelSmall)
                                    }

                                    FilledTonalButton(
                                        onClick = { uriHandler.openUri("https://huggingface.co/settings/tokens") },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("2. Get Token ↗", style = MaterialTheme.typography.labelSmall)
                                    }
                                }

                                // Token Input with Clipboard Paste
                                OutlinedTextField(
                                    value = hfToken,
                                    onValueChange = { viewModel.updateHfToken(it) },
                                    label = { Text("Hugging Face Read Token (hf_...)") },
                                    placeholder = { Text("hf_xxxxxxxxxxxxxxxxxxxx") },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = {
                                                val clip = clipboardManager.getText()?.text
                                                if (!clip.isNullOrBlank()) {
                                                    viewModel.updateHfToken(clip.trim())
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentPaste,
                                                contentDescription = "Paste Token",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !isDownloading,
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )
                            }
                        }
                    }
                }

                // Custom Model Card (Advanced)
                ModelSelectionCard(
                    preset = ModelPreset.CUSTOM,
                    isSelected = selectedPreset.id == ModelPreset.CUSTOM.id,
                    badgeColor = MaterialTheme.colorScheme.surfaceVariant,
                    badgeTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { if (!isDownloading) viewModel.selectPreset(ModelPreset.CUSTOM) }
                ) {
                    AnimatedVisibility(visible = selectedPreset.id == ModelPreset.CUSTOM.id) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = customUrl,
                                onValueChange = { viewModel.updateCustomUrl(it) },
                                label = { Text("Direct HTTPS URL (.litertlm)") },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isDownloading,
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = hfToken,
                                onValueChange = { viewModel.updateHfToken(it) },
                                label = { Text("Auth Token (Optional)") },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isDownloading,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }

            // Download Status / Progress Area
            AnimatedVisibility(visible = state !is DownloadState.Idle) {
                DownloadStatusCard(state = state)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Primary Call to Action Button
            Button(
                onClick = { viewModel.startDownload() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .shadow(
                        elevation = if (isDownloading) 0.dp else 8.dp,
                        shape = RoundedCornerShape(16.dp),
                        ambientColor = ElectricCyanAccent,
                        spotColor = ElectricCyanAccent
                    ),
                enabled = !isDownloading,
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
                        text = "Downloading AI Engine...",
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
                            text = if (state is DownloadState.Error) "Retry Download" else "Get Started (Download AI)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
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
                text = "Thwips actionable tasks out of the noise. Notifications and screenshots stay 100% on your device silicon.",
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
    accentColor: Color = ElectricCyanAccent
) {
    Box(
        modifier = modifier
            .size(76.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.3f),
                        accentColor.copy(alpha = 0.08f),
                        Color.Transparent
                    )
                )
            )
            .border(2.dp, accentColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(46.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.minDimension / 2f
            val numSpokes = 8
            val numRings = 3

            val angles = (0 until numSpokes).map { it * (2 * PI / numSpokes) - (PI / 2) }

            // 1. Draw radial spokes
            angles.forEach { angle ->
                val endX = center.x + (maxRadius * cos(angle)).toFloat()
                val endY = center.y + (maxRadius * sin(angle)).toFloat()
                drawLine(
                    color = accentColor,
                    start = center,
                    end = Offset(endX, endY),
                    strokeWidth = 2.4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // 2. Draw concentric web polygons
            for (ring in 1..numRings) {
                val ringRadius = maxRadius * (ring.toFloat() / numRings)
                val path = Path()
                angles.forEachIndexed { i, angle ->
                    val x = center.x + (ringRadius * cos(angle)).toFloat()
                    val y = center.y + (ringRadius * sin(angle)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(
                    path = path,
                    color = accentColor,
                    style = Stroke(
                        width = 2.2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            // 3. Center bright glowing core
            drawCircle(
                color = Color.White,
                radius = 3.5.dp.toPx(),
                center = center
            )
        }
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
            label = "100% Local",
            modifier = Modifier.weight(1f)
        )
        ValuePropBadge(
            icon = Icons.Default.Bolt,
            label = "Zero Cloud",
            modifier = Modifier.weight(1f)
        )
        ValuePropBadge(
            icon = Icons.Default.TaskAlt,
            label = "Smart Tasks",
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
    preset: ModelPreset,
    isSelected: Boolean,
    badgeColor: Color,
    badgeTextColor: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
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
                        text = preset.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeColor
                ) {
                    Text(
                        text = preset.size,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeTextColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            content()
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
                            text = "Downloading AI Engine...",
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
                            contentDescription = "Error",
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
                            contentDescription = "Success",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Model download complete! Initializing LiteRT engine...",
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
