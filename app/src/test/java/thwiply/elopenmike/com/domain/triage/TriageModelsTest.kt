package thwiply.elopenmike.com.domain.triage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class TriageModelsTest {
    @Test
    fun `minimal manual triage record is valid`() {
        val item = TriageItem(
            id = "item-1",
            displayTitle = "Buy milk",
            displaySummary = null,
            source = SourceReference.manual(),
            isHighPriority = false,
            createdAtEpochMillis = 100,
            dueAtEpochMillis = null,
            completedAtEpochMillis = null,
        )
        val decision = TriageDecision(
            id = "decision-1",
            triageItemId = item.id,
            category = TriageCategory.NOW,
            explanation = "Added manually",
            origin = DecisionOrigin.MANUAL,
            decidedAtEpochMillis = 100,
        )

        val record = TriageRecord(item, decision)

        assertEquals("Buy milk", record.item.displayTitle)
        assertEquals(SourceKind.MANUAL, record.item.source.kind)
        assertNull(record.item.source.packageName)
    }

    @Test
    fun `notification provenance requires a package name`() {
        assertThrows(IllegalArgumentException::class.java) {
            SourceReference(
                kind = SourceKind.NOTIFICATION,
                packageName = null,
                appLabel = "Mail",
                stableKeyHash = null,
            )
        }
    }

    @Test
    fun `notification provenance accepts only a sha256 stable key hash`() {
        assertThrows(IllegalArgumentException::class.java) {
            SourceReference.notification(
                packageName = "com.example.mail",
                appLabel = "Mail",
                stableKeyHash = "raw-notification-key",
            )
        }
    }

    @Test
    fun `manual provenance cannot carry notification identifiers`() {
        assertThrows(IllegalArgumentException::class.java) {
            SourceReference(
                kind = SourceKind.MANUAL,
                packageName = "com.example.mail",
                appLabel = "Manual",
                stableKeyHash = null,
            )
        }
    }

    @Test
    fun `display title is bounded`() {
        assertThrows(IllegalArgumentException::class.java) {
            TriageItem(
                id = "item-1",
                displayTitle = "x".repeat(TriageItem.MAX_DISPLAY_TITLE_LENGTH + 1),
                displaySummary = null,
                source = SourceReference.manual(),
                isHighPriority = false,
                createdAtEpochMillis = 100,
                dueAtEpochMillis = null,
                completedAtEpochMillis = null,
            )
        }
    }

    @Test
    fun `completion cannot predate creation`() {
        assertThrows(IllegalArgumentException::class.java) {
            TriageItem(
                id = "item-1",
                displayTitle = "Buy milk",
                displaySummary = null,
                source = SourceReference.manual(),
                isHighPriority = false,
                createdAtEpochMillis = 100,
                dueAtEpochMillis = null,
                completedAtEpochMillis = 99,
            )
        }
    }

    @Test
    fun `record rejects a decision for another item`() {
        val item = validItem()
        val decision = validDecision().copy(triageItemId = "item-2")

        assertThrows(IllegalArgumentException::class.java) {
            TriageRecord(item, decision)
        }
    }

    @Test
    fun `correction must change the category`() {
        assertThrows(IllegalArgumentException::class.java) {
            UserCorrection(
                id = "correction-1",
                triageItemId = "item-1",
                previousCategory = TriageCategory.LATER,
                correctedCategory = TriageCategory.LATER,
                createdAtEpochMillis = 200,
                createdRuleId = null,
            )
        }
    }

    @Test
    fun `rule update cannot predate rule creation`() {
        assertThrows(IllegalArgumentException::class.java) {
            UserRule(
                id = "rule-1",
                packageName = "com.example.mail",
                channelId = null,
                action = RuleAction.PRIORITIZE,
                isEnabled = true,
                createdAtEpochMillis = 200,
                updatedAtEpochMillis = 199,
            )
        }
    }

    private fun validItem() = TriageItem(
        id = "item-1",
        displayTitle = "Buy milk",
        displaySummary = null,
        source = SourceReference.manual(),
        isHighPriority = false,
        createdAtEpochMillis = 100,
        dueAtEpochMillis = null,
        completedAtEpochMillis = null,
    )

    private fun validDecision() = TriageDecision(
        id = "decision-1",
        triageItemId = "item-1",
        category = TriageCategory.NOW,
        explanation = "Added manually",
        origin = DecisionOrigin.MANUAL,
        decidedAtEpochMillis = 100,
    )
}
