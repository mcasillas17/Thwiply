package thwiply.elopenmike.com.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThwiplyMigrationTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ThwiplyDatabase::class.java,
    )

    @Test
    fun migrationOneToTwoPreservesAllSupportedDataAndBackfillsRetention() {
        migrationHelper.createDatabase(TEST_DATABASE_NAME, 1).apply {
            execSQL(
                """
                INSERT INTO triage_items (
                    id, display_title, display_summary, source_kind,
                    source_package_name, source_app_label, source_stable_key_hash,
                    is_high_priority, created_at_epoch_millis,
                    due_at_epoch_millis, completed_at_epoch_millis
                ) VALUES (
                    'notification-item', 'Call Alex', 'Before noon', 'NOTIFICATION',
                    'com.example.messages', 'Messages', '${"a".repeat(64)}',
                    1, 100, 300, NULL
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO triage_items (
                    id, display_title, display_summary, source_kind,
                    source_package_name, source_app_label, source_stable_key_hash,
                    is_high_priority, created_at_epoch_millis,
                    due_at_epoch_millis, completed_at_epoch_millis
                ) VALUES (
                    'manual-item', 'Buy milk', NULL, 'MANUAL',
                    NULL, 'Manual', NULL,
                    0, 200, NULL, NULL
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO user_rules (
                    id, package_name, channel_id, action, is_enabled,
                    created_at_epoch_millis, updated_at_epoch_millis
                ) VALUES (
                    'rule-1', 'com.example.messages', 'priority', 'PRIORITIZE', 1,
                    400, 500
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO triage_decisions (
                    id, triage_item_id, category, explanation, origin,
                    decided_at_epoch_millis
                ) VALUES
                    ('decision-1', 'notification-item', 'NOW', 'Direct request',
                     'ON_DEVICE_MODEL', 100),
                    ('decision-2', 'manual-item', 'NOW', 'Added manually',
                     'MANUAL', 200)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO user_corrections (
                    id, triage_item_id, previous_category, corrected_category,
                    created_at_epoch_millis, created_rule_id
                ) VALUES (
                    'correction-1', 'notification-item', 'LATER', 'NOW', 500, 'rule-1'
                )
                """.trimIndent(),
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE_NAME,
            2,
            true,
            MIGRATION_1_2,
        )

        migrated.query(
            """
            SELECT display_title, display_summary, source_package_name,
                   is_high_priority, due_at_epoch_millis,
                   retention_expires_at_epoch_millis
            FROM triage_items WHERE id = 'notification-item'
            """.trimIndent(),
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("Call Alex", cursor.getString(0))
            assertEquals("Before noon", cursor.getString(1))
            assertEquals("com.example.messages", cursor.getString(2))
            assertEquals(1, cursor.getInt(3))
            assertEquals(300L, cursor.getLong(4))
            assertEquals(100L + DEFAULT_NOTIFICATION_RETENTION_MILLIS, cursor.getLong(5))
        }
        migrated.query(
            "SELECT retention_expires_at_epoch_millis FROM triage_items WHERE id = 'manual-item'",
        ).use { cursor ->
            cursor.moveToFirst()
            assertNull(if (cursor.isNull(0)) null else cursor.getLong(0))
        }
        migrated.query(
            """
            SELECT triage_item_id, category, explanation, origin, decided_at_epoch_millis
            FROM triage_decisions WHERE id = 'decision-1'
            """.trimIndent(),
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("notification-item", cursor.getString(0))
            assertEquals("NOW", cursor.getString(1))
            assertEquals("Direct request", cursor.getString(2))
            assertEquals("ON_DEVICE_MODEL", cursor.getString(3))
            assertEquals(100L, cursor.getLong(4))
        }
        migrated.query(
            """
            SELECT package_name, channel_id, action, is_enabled,
                   created_at_epoch_millis, updated_at_epoch_millis
            FROM user_rules WHERE id = 'rule-1'
            """.trimIndent(),
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("com.example.messages", cursor.getString(0))
            assertEquals("priority", cursor.getString(1))
            assertEquals("PRIORITIZE", cursor.getString(2))
            assertEquals(1, cursor.getInt(3))
            assertEquals(400L, cursor.getLong(4))
            assertEquals(500L, cursor.getLong(5))
        }
        migrated.query(
            """
            SELECT triage_item_id, previous_category, corrected_category,
                   created_at_epoch_millis, created_rule_id
            FROM user_corrections WHERE id = 'correction-1'
            """.trimIndent(),
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("notification-item", cursor.getString(0))
            assertEquals("LATER", cursor.getString(1))
            assertEquals("NOW", cursor.getString(2))
            assertEquals(500L, cursor.getLong(3))
            assertEquals("rule-1", cursor.getString(4))
        }
        assertCount(migrated, "triage_decisions", 2)
        assertCount(migrated, "user_corrections", 1)
        assertCount(migrated, "user_rules", 1)
        migrated.close()
    }

    private fun assertCount(
        database: androidx.sqlite.db.SupportSQLiteDatabase,
        tableName: String,
        expected: Int,
    ) {
        require(tableName in COUNTED_TABLES) { "Unsupported migration test table" }
        database.query("SELECT COUNT(*) FROM $tableName").use { cursor ->
            cursor.moveToFirst()
            assertEquals(expected, cursor.getInt(0))
        }
    }

    private companion object {
        const val TEST_DATABASE_NAME = "thwiply-migration-test.db"
        val COUNTED_TABLES = setOf(
            "triage_decisions",
            "user_corrections",
            "user_rules",
        )
    }
}
