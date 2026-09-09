package thwiply.elopenmike.com.ui.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import thwiply.elopenmike.com.R
import thwiply.elopenmike.com.domain.triage.SourceKind
import thwiply.elopenmike.com.domain.triage.TriageItem
import thwiply.elopenmike.com.ui.theme.ElectricCyanAccent
import thwiply.elopenmike.com.ui.main.AppAlertDialog
import thwiply.elopenmike.com.ui.main.AppTopBar
import thwiply.elopenmike.com.ui.main.LocalCompactHeight
import androidx.compose.ui.platform.testTag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val operationFailure by viewModel.operationFailure.collectAsStateWithLifecycle()
    val taskInputFailure by viewModel.taskInputFailure.collectAsStateWithLifecycle()
    val cleanupWarning by viewModel.cleanupWarning.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    val compactHeight = LocalCompactHeight.current

    // Entry and every resume are cleanup and visibility boundaries: a device that slept
    // through an expiry must not come back showing an expired notification-derived record.
    // Nothing to undo on pause: the Room observation and its expiry timer are owned by the
    // lifecycle-aware collection above, which stops them when this screen stops.
    LifecycleResumeEffect(viewModel) {
        viewModel.onTodayEntered()
        onPauseOrDispose { }
    }

    val tasks = (uiState as? TodayUiState.Content)?.tasks.orEmpty()
    val filteredTasks = remember(tasks, selectedFilter) {
        when (selectedFilter) {
            TaskFilter.ALL -> tasks
            TaskFilter.NOTIFICATIONS -> tasks.filter {
                it.sourceKind == SourceKind.NOTIFICATION
            }
            TaskFilter.HIGH_PRIORITY -> tasks.filter { it.isHighPriority }
            TaskFilter.COMPLETED -> tasks.filter { it.isCompleted }
        }
    }
    // Placeholder measurement must not clamp the saved task position during a delayed reload.
    val taskListState = rememberLazyListState()
    val statusListState = rememberLazyListState()

    LaunchedEffect(operationFailure) {
        if (operationFailure != null) {
            snackbarHostState.showSnackbar("Thwiply couldn't save that change.")
            viewModel.dismissOperationFailure()
        }
    }

    LaunchedEffect(taskInputFailure) {
        val failure = taskInputFailure ?: return@LaunchedEffect
        val message = when (failure) {
            TaskInputFailure.BLANK_TITLE -> "Enter a task description."
            TaskInputFailure.TITLE_TOO_LONG -> {
                "Task descriptions can use up to ${TriageItem.MAX_DISPLAY_TITLE_LENGTH} characters."
            }
            TaskInputFailure.SUMMARY_TOO_LONG -> {
                "Task notes can use up to ${TriageItem.MAX_DISPLAY_SUMMARY_LENGTH} characters."
            }
        }
        snackbarHostState.showSnackbar(message)
        viewModel.dismissTaskInputFailure()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (!compactHeight) TodayTopBar(activeCount = tasks.count { !it.isCompleted })
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(18.dp),
                    spotColor = ElectricCyanAccent,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add task")
                    Text("Thwip Task", fontWeight = FontWeight.Bold)
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            state = if (uiState is TodayUiState.Content && filteredTasks.isNotEmpty())
                taskListState else statusListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .testTag("today-scroll"),
            contentPadding = PaddingValues(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Content always has filters: one real chrome item keeps saved task indices
            // stable across header/warning changes without empty sentinel spacing.
            if (compactHeight || cleanupWarning || uiState is TodayUiState.Content) {
                item("chrome") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (compactHeight) TodayTopBar(activeCount = tasks.count { !it.isCompleted })
                        if (cleanupWarning) CleanupWarningBanner(onRetry = viewModel::retryCleanup)
                        if (uiState is TodayUiState.Content) {
                            FilterRow(selectedFilter = selectedFilter, onSelect = viewModel::setFilter)
                        }
                    }
                }
            }
            when (uiState) {
                TodayUiState.Loading -> item { CenteredStatus {
                    CircularProgressIndicator()
                } }
                TodayUiState.Empty -> item { EmptyTodayState() }
                TodayUiState.StorageError -> item { StorageErrorState() }
                is TodayUiState.Content -> {
                    if (filteredTasks.isEmpty()) {
                        item { FilterEmptyState() }
                    } else {
                        items(filteredTasks, key = TaskItem::id) { task ->
                            Box(Modifier.padding(horizontal = 16.dp)) {
                                TaskCard(
                                    task = task,
                                    onToggle = { viewModel.toggleTask(task.id) },
                                    onDelete = { viewModel.deleteTask(task.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        QuickAddDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { title, subtitle, isHighPriority ->
                viewModel.addTask(title, subtitle, isHighPriority)
                showAddDialog = false
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodayTopBar(activeCount: Int) {
    val date = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))
    }
    AppTopBar {
            Column {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Today",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        ),
                    ) {
                        Text(
                            text = "$activeCount saved",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                Text(
                    text = date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
    }
}

@Composable
private fun FilterRow(
    selectedFilter: TaskFilter,
    onSelect: (TaskFilter) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(TaskFilter.entries) { filter ->
            val selected = filter == selectedFilter
            FilterChip(
                selected = selected,
                onClick = { onSelect(filter) },
                label = { Text(filter.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                shape = RoundedCornerShape(14.dp),
            )
        }
    }
}

/** Nonblocking notice: cleanup failed, records stay usable, and a retry is one tap away. */
@Composable
private fun CleanupWarningBanner(onRetry: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = stringResource(R.string.today_cleanup_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                // The live region owns the message text, so it has content to announce.
                modifier = Modifier
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.today_cleanup_retry))
            }
        }
    }
}

@Composable
private fun EmptyTodayState() {
    CenteredStatus {
        Icon(
            imageVector = Icons.Default.TaskAlt,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
        )
        Text("No saved tasks yet", fontWeight = FontWeight.Bold)
        Text(
            text = "Add a task to keep it here across app restarts.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FilterEmptyState() {
    CenteredStatus {
        Icon(Icons.Default.TaskAlt, contentDescription = null, modifier = Modifier.size(48.dp))
        Text("No tasks match this filter", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StorageErrorState() {
    CenteredStatus {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Text("Saved tasks are unavailable", fontWeight = FontWeight.Bold)
        Text(
            text = "Thwiply couldn't read local storage. Try opening Today again.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CenteredStatus(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp)
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun TaskCard(
    task: TaskItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by rememberSaveable(task.id) { mutableStateOf(false) }
    val containerColor by animateColorAsState(
        targetValue = if (task.isCompleted) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = spring(),
        label = "taskCardColor",
    )
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        border = BorderStroke(
            1.dp,
            if (task.isHighPriority && !task.isCompleted) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CompletionButton(completed = task.isCompleted, onToggle = onToggle)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (task.isCompleted) {
                            TextDecoration.LineThrough
                        } else {
                            TextDecoration.None
                        },
                    )
                    task.subtitle?.let { subtitle ->
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (expanded) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SourceBadge(task)
                        task.dueAtEpochMillis?.let { DueTime(it) }
                    }
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Delete",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = task.decisionExplanation,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletionButton(completed: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(top = 2.dp)
            .size(24.dp)
            .clip(CircleShape)
            .background(if (completed) MaterialTheme.colorScheme.primary else Color.Transparent)
            .border(
                width = 2.dp,
                color = if (completed) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                shape = CircleShape,
            )
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        if (completed) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Completed",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun SourceBadge(task: TaskItem) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = if (task.sourceKind == SourceKind.NOTIFICATION) {
                    Icons.Default.Notifications
                } else {
                    Icons.Default.Edit
                },
                contentDescription = null,
                modifier = Modifier.size(12.dp),
            )
            Text(text = task.sourceLabel, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun DueTime(dueAtEpochMillis: Long) {
    val formatted = remember(dueAtEpochMillis) {
        Instant.ofEpochMilli(dueAtEpochMillis)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Schedule,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = formatted,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun QuickAddDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String?, Boolean) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var subtitle by rememberSaveable { mutableStateOf("") }
    var isHighPriority by rememberSaveable { mutableStateOf(false) }
    var titleTruncated by rememberSaveable { mutableStateOf(false) }
    var notesTruncated by rememberSaveable { mutableStateOf(false) }
    val titleDraftLimit = TriageItem.MAX_DISPLAY_TITLE_LENGTH + 1
    val notesDraftLimit = TriageItem.MAX_DISPLAY_SUMMARY_LENGTH + 1
    val titleTooLong = title.trim().length > TriageItem.MAX_DISPLAY_TITLE_LENGTH
    val summaryTooLong = subtitle.trim().length > TriageItem.MAX_DISPLAY_SUMMARY_LENGTH
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Thwip New Task", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = title,
                    maxLines = 3,
                    onValueChange = {
                        titleTruncated = it.length > titleDraftLimit
                        title = it.take(titleDraftLimit)
                    },
                    label = { Text("Task description") },
                    isError = titleTooLong || titleTruncated,
                    supportingText = if (titleTruncated) {
                        { Text(
                            stringResource(R.string.task_draft_truncated, titleDraftLimit),
                            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        ) }
                    } else if (titleTooLong) {
                        { Text(stringResource(R.string.task_draft_limit, TriageItem.MAX_DISPLAY_TITLE_LENGTH)) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = subtitle,
                    maxLines = 4,
                    onValueChange = {
                        notesTruncated = it.length > notesDraftLimit
                        subtitle = it.take(notesDraftLimit)
                    },
                    label = { Text("Notes (optional)") },
                    isError = summaryTooLong || notesTruncated,
                    supportingText = if (notesTruncated) {
                        { Text(
                            stringResource(R.string.task_draft_truncated, notesDraftLimit),
                            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        ) }
                    } else if (summaryTooLong) {
                        { Text(stringResource(R.string.task_draft_limit, TriageItem.MAX_DISPLAY_SUMMARY_LENGTH)) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("High priority", modifier = Modifier.weight(1f))
                    Switch(
                        checked = isHighPriority,
                        onCheckedChange = { isHighPriority = it },
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(title, subtitle, isHighPriority) },
                enabled = title.isNotBlank() && !titleTooLong && !summaryTooLong &&
                    !titleTruncated && !notesTruncated,
            ) {
                Text("Add Task")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
