package thwiply.elopenmike.com.data.local.entity

import androidx.room.Embedded
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(
    tableName = "triage_items",
    indices = [
        Index(value = ["source_kind", "created_at_epoch_millis"]),
        Index(value = ["source_kind", "retention_expires_at_epoch_millis"]),
    ],
)
data class TriageItemEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "display_title")
    val displayTitle: String,
    @ColumnInfo(name = "display_summary")
    val displaySummary: String?,
    @ColumnInfo(name = "source_kind")
    val sourceKind: String,
    @ColumnInfo(name = "source_package_name")
    val sourcePackageName: String?,
    @ColumnInfo(name = "source_app_label")
    val sourceAppLabel: String,
    @ColumnInfo(name = "source_stable_key_hash")
    val sourceStableKeyHash: String?,
    @ColumnInfo(name = "is_high_priority")
    val isHighPriority: Boolean,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @ColumnInfo(name = "due_at_epoch_millis")
    val dueAtEpochMillis: Long?,
    @ColumnInfo(name = "completed_at_epoch_millis")
    val completedAtEpochMillis: Long?,
    @ColumnInfo(name = "retention_expires_at_epoch_millis")
    val retentionExpiresAtEpochMillis: Long? = null,
)

@Entity(
    tableName = "triage_decisions",
    foreignKeys = [
        ForeignKey(
            entity = TriageItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["triage_item_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["triage_item_id"], unique = true),
    ],
)
data class TriageDecisionEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "triage_item_id")
    val triageItemId: String,
    val category: String,
    val explanation: String,
    val origin: String,
    @ColumnInfo(name = "decided_at_epoch_millis")
    val decidedAtEpochMillis: Long,
)

@Entity(
    tableName = "user_rules",
    indices = [
        Index(value = ["package_name", "channel_id"]),
    ],
)
data class UserRuleEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "package_name")
    val packageName: String,
    @ColumnInfo(name = "channel_id")
    val channelId: String?,
    val action: String,
    @ColumnInfo(name = "is_enabled")
    val isEnabled: Boolean,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "user_corrections",
    foreignKeys = [
        ForeignKey(
            entity = TriageItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["triage_item_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = UserRuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["created_rule_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["triage_item_id"]),
        Index(value = ["created_rule_id"]),
    ],
)
data class UserCorrectionEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "triage_item_id")
    val triageItemId: String,
    @ColumnInfo(name = "previous_category")
    val previousCategory: String,
    @ColumnInfo(name = "corrected_category")
    val correctedCategory: String,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @ColumnInfo(name = "created_rule_id")
    val createdRuleId: String?,
)

data class TriageItemWithDecision(
    @Embedded
    val item: TriageItemEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "triage_item_id",
    )
    val decision: TriageDecisionEntity,
)
