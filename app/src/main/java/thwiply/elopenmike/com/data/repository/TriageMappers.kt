package thwiply.elopenmike.com.data.repository

import thwiply.elopenmike.com.data.local.entity.TriageDecisionEntity
import thwiply.elopenmike.com.data.local.entity.TriageItemEntity
import thwiply.elopenmike.com.data.local.entity.TriageItemWithDecision
import thwiply.elopenmike.com.data.local.entity.UserCorrectionEntity
import thwiply.elopenmike.com.data.local.entity.UserRuleEntity
import thwiply.elopenmike.com.domain.triage.DecisionOrigin
import thwiply.elopenmike.com.domain.triage.RuleAction
import thwiply.elopenmike.com.domain.triage.SourceKind
import thwiply.elopenmike.com.domain.triage.SourceReference
import thwiply.elopenmike.com.domain.triage.TriageCategory
import thwiply.elopenmike.com.domain.triage.TriageDecision
import thwiply.elopenmike.com.domain.triage.TriageItem
import thwiply.elopenmike.com.domain.triage.TriageRecord
import thwiply.elopenmike.com.domain.triage.UserCorrection
import thwiply.elopenmike.com.domain.triage.UserRule

internal fun TriageItemWithDecision.toDomain() = TriageRecord(
    item = item.toDomain(),
    decision = decision.toDomain(),
)

internal fun TriageItemEntity.toDomain() = TriageItem(
    id = id,
    displayTitle = displayTitle,
    displaySummary = displaySummary,
    source = SourceReference(
        kind = enumValueOf<SourceKind>(sourceKind),
        packageName = sourcePackageName,
        appLabel = sourceAppLabel,
        stableKeyHash = sourceStableKeyHash,
    ),
    isHighPriority = isHighPriority,
    createdAtEpochMillis = createdAtEpochMillis,
    dueAtEpochMillis = dueAtEpochMillis,
    completedAtEpochMillis = completedAtEpochMillis,
)

internal fun TriageItem.toEntity() = TriageItemEntity(
    id = id,
    displayTitle = displayTitle,
    displaySummary = displaySummary,
    sourceKind = source.kind.name,
    sourcePackageName = source.packageName,
    sourceAppLabel = source.appLabel,
    sourceStableKeyHash = source.stableKeyHash,
    isHighPriority = isHighPriority,
    createdAtEpochMillis = createdAtEpochMillis,
    dueAtEpochMillis = dueAtEpochMillis,
    completedAtEpochMillis = completedAtEpochMillis,
    retentionExpiresAtEpochMillis = when (source.kind) {
        SourceKind.MANUAL -> null
        SourceKind.NOTIFICATION -> notificationRetentionExpiry(createdAtEpochMillis)
    },
)

internal fun TriageDecisionEntity.toDomain() = TriageDecision(
    id = id,
    triageItemId = triageItemId,
    category = enumValueOf<TriageCategory>(category),
    explanation = explanation,
    origin = enumValueOf<DecisionOrigin>(origin),
    decidedAtEpochMillis = decidedAtEpochMillis,
)

internal fun TriageDecision.toEntity() = TriageDecisionEntity(
    id = id,
    triageItemId = triageItemId,
    category = category.name,
    explanation = explanation,
    origin = origin.name,
    decidedAtEpochMillis = decidedAtEpochMillis,
)

internal fun UserCorrectionEntity.toDomain() = UserCorrection(
    id = id,
    triageItemId = triageItemId,
    previousCategory = enumValueOf<TriageCategory>(previousCategory),
    correctedCategory = enumValueOf<TriageCategory>(correctedCategory),
    createdAtEpochMillis = createdAtEpochMillis,
    createdRuleId = createdRuleId,
)

internal fun UserCorrection.toEntity() = UserCorrectionEntity(
    id = id,
    triageItemId = triageItemId,
    previousCategory = previousCategory.name,
    correctedCategory = correctedCategory.name,
    createdAtEpochMillis = createdAtEpochMillis,
    createdRuleId = createdRuleId,
)

internal fun UserRuleEntity.toDomain() = UserRule(
    id = id,
    packageName = packageName,
    channelId = channelId,
    action = enumValueOf<RuleAction>(action),
    isEnabled = isEnabled,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

internal fun UserRule.toEntity() = UserRuleEntity(
    id = id,
    packageName = packageName,
    channelId = channelId,
    action = action.name,
    isEnabled = isEnabled,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

private fun notificationRetentionExpiry(createdAtEpochMillis: Long): Long {
    val retentionMillis = thwiply.elopenmike.com.data.local.DEFAULT_NOTIFICATION_RETENTION_MILLIS
    return if (createdAtEpochMillis > Long.MAX_VALUE - retentionMillis) {
        Long.MAX_VALUE
    } else {
        createdAtEpochMillis + retentionMillis
    }
}
