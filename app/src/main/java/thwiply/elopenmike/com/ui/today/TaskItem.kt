package thwiply.elopenmike.com.ui.today

import thwiply.elopenmike.com.domain.triage.SourceKind
import thwiply.elopenmike.com.domain.triage.TriageRecord

data class TaskItem(
    val id: String,
    val title: String,
    val subtitle: String?,
    val sourceKind: SourceKind,
    val sourceLabel: String,
    val dueAtEpochMillis: Long?,
    val isCompleted: Boolean,
    val isHighPriority: Boolean,
    val decisionExplanation: String,
    val createdAtEpochMillis: Long,
)

internal fun TriageRecord.toTaskItem() = TaskItem(
    id = item.id,
    title = item.displayTitle,
    subtitle = item.displaySummary,
    sourceKind = item.source.kind,
    sourceLabel = item.source.appLabel,
    dueAtEpochMillis = item.dueAtEpochMillis,
    isCompleted = item.completedAtEpochMillis != null,
    isHighPriority = item.isHighPriority,
    decisionExplanation = decision.explanation,
    createdAtEpochMillis = item.createdAtEpochMillis,
)
