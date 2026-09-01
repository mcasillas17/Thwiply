package thwiply.elopenmike.com.domain.triage

enum class SourceKind {
    MANUAL,
    NOTIFICATION,
}

data class SourceReference(
    val kind: SourceKind,
    val packageName: String?,
    val appLabel: String,
    val stableKeyHash: String?,
) {
    init {
        requireBoundedText("source app label", appLabel, MAX_APP_LABEL_LENGTH)
        when (kind) {
            SourceKind.MANUAL -> {
                require(packageName == null) { "manual source package name must be absent" }
                require(stableKeyHash == null) { "manual source stable key hash must be absent" }
            }

            SourceKind.NOTIFICATION -> {
                requireBoundedText(
                    fieldName = "notification source package name",
                    value = packageName,
                    maxLength = MAX_PACKAGE_NAME_LENGTH,
                )
                stableKeyHash?.let {
                    require(SHA_256_REGEX.matches(it)) {
                        "notification source stable key hash must be lowercase SHA-256"
                    }
                }
            }
        }
    }

    companion object {
        const val MAX_APP_LABEL_LENGTH = 100
        const val MAX_PACKAGE_NAME_LENGTH = 255

        private val SHA_256_REGEX = Regex("[0-9a-f]{64}")

        fun manual() = SourceReference(
            kind = SourceKind.MANUAL,
            packageName = null,
            appLabel = "Manual",
            stableKeyHash = null,
        )

        fun notification(
            packageName: String,
            appLabel: String,
            stableKeyHash: String?,
        ) = SourceReference(
            kind = SourceKind.NOTIFICATION,
            packageName = packageName,
            appLabel = appLabel,
            stableKeyHash = stableKeyHash,
        )
    }
}

data class TriageItem(
    val id: String,
    val displayTitle: String,
    val displaySummary: String?,
    val source: SourceReference,
    val isHighPriority: Boolean,
    val createdAtEpochMillis: Long,
    val dueAtEpochMillis: Long?,
    val completedAtEpochMillis: Long?,
) {
    init {
        requireIdentifier("triage item id", id)
        requireBoundedText("triage item display title", displayTitle, MAX_DISPLAY_TITLE_LENGTH)
        displaySummary?.let {
            requireBoundedText("triage item display summary", it, MAX_DISPLAY_SUMMARY_LENGTH)
        }
        requireNonNegativeTimestamp("triage item creation timestamp", createdAtEpochMillis)
        dueAtEpochMillis?.let {
            requireNonNegativeTimestamp("triage item due timestamp", it)
        }
        completedAtEpochMillis?.let {
            requireNonNegativeTimestamp("triage item completion timestamp", it)
            require(it >= createdAtEpochMillis) {
                "triage item completion timestamp cannot predate creation"
            }
        }
    }

    companion object {
        const val MAX_DISPLAY_TITLE_LENGTH = 200
        const val MAX_DISPLAY_SUMMARY_LENGTH = 500
    }
}

enum class TriageCategory {
    NOW,
    LATER,
    NEEDS_REVIEW,
}

enum class DecisionOrigin {
    MANUAL,
    USER_RULE,
    ON_DEVICE_MODEL,
}

data class TriageDecision(
    val id: String,
    val triageItemId: String,
    val category: TriageCategory,
    val explanation: String,
    val origin: DecisionOrigin,
    val decidedAtEpochMillis: Long,
) {
    init {
        requireIdentifier("triage decision id", id)
        requireIdentifier("triage decision item id", triageItemId)
        requireBoundedText("triage decision explanation", explanation, MAX_EXPLANATION_LENGTH)
        requireNonNegativeTimestamp("triage decision timestamp", decidedAtEpochMillis)
    }

    companion object {
        const val MAX_EXPLANATION_LENGTH = 300
    }
}

data class TriageRecord(
    val item: TriageItem,
    val decision: TriageDecision,
) {
    init {
        require(item.id == decision.triageItemId) {
            "triage decision must belong to the triage item"
        }
    }
}

data class UserCorrection(
    val id: String,
    val triageItemId: String,
    val previousCategory: TriageCategory,
    val correctedCategory: TriageCategory,
    val createdAtEpochMillis: Long,
    val createdRuleId: String?,
) {
    init {
        requireIdentifier("user correction id", id)
        requireIdentifier("user correction item id", triageItemId)
        require(previousCategory != correctedCategory) {
            "user correction must change the triage category"
        }
        requireNonNegativeTimestamp("user correction timestamp", createdAtEpochMillis)
        createdRuleId?.let { requireIdentifier("user correction rule id", it) }
    }
}

enum class RuleAction {
    PRIORITIZE,
    DEFER,
    IGNORE,
}

data class UserRule(
    val id: String,
    val packageName: String,
    val channelId: String?,
    val action: RuleAction,
    val isEnabled: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    init {
        requireIdentifier("user rule id", id)
        requireBoundedText(
            fieldName = "user rule package name",
            value = packageName,
            maxLength = SourceReference.MAX_PACKAGE_NAME_LENGTH,
        )
        channelId?.let {
            requireBoundedText("user rule channel id", it, MAX_CHANNEL_ID_LENGTH)
        }
        requireNonNegativeTimestamp("user rule creation timestamp", createdAtEpochMillis)
        requireNonNegativeTimestamp("user rule update timestamp", updatedAtEpochMillis)
        require(updatedAtEpochMillis >= createdAtEpochMillis) {
            "user rule update timestamp cannot predate creation"
        }
    }

    companion object {
        const val MAX_CHANNEL_ID_LENGTH = 255
    }
}

private const val MAX_IDENTIFIER_LENGTH = 128

private fun requireIdentifier(fieldName: String, value: String) {
    requireBoundedText(fieldName, value, MAX_IDENTIFIER_LENGTH)
}

private fun requireBoundedText(fieldName: String, value: String?, maxLength: Int) {
    require(!value.isNullOrBlank()) { "$fieldName must not be blank" }
    require(value.length <= maxLength) { "$fieldName exceeds its maximum length" }
}

private fun requireNonNegativeTimestamp(fieldName: String, value: Long) {
    require(value >= 0) { "$fieldName must not be negative" }
}
