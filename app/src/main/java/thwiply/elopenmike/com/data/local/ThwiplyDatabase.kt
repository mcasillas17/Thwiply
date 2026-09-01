package thwiply.elopenmike.com.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import thwiply.elopenmike.com.data.local.dao.CorrectionDao
import thwiply.elopenmike.com.data.local.dao.DataLifecycleDao
import thwiply.elopenmike.com.data.local.dao.TriageDao
import thwiply.elopenmike.com.data.local.dao.UserRuleDao
import thwiply.elopenmike.com.data.local.entity.TriageDecisionEntity
import thwiply.elopenmike.com.data.local.entity.TriageItemEntity
import thwiply.elopenmike.com.data.local.entity.UserCorrectionEntity
import thwiply.elopenmike.com.data.local.entity.UserRuleEntity

@Database(
    entities = [
        TriageItemEntity::class,
        TriageDecisionEntity::class,
        UserCorrectionEntity::class,
        UserRuleEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class ThwiplyDatabase : RoomDatabase() {
    abstract fun triageDao(): TriageDao

    abstract fun correctionDao(): CorrectionDao

    abstract fun userRuleDao(): UserRuleDao

    abstract fun dataLifecycleDao(): DataLifecycleDao
}

const val DEFAULT_NOTIFICATION_RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1_000
const val THWIPLY_DATABASE_NAME = "thwiply.db"

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE triage_items " +
                "ADD COLUMN retention_expires_at_epoch_millis INTEGER",
        )
        db.execSQL(
            """
            UPDATE triage_items
            SET retention_expires_at_epoch_millis =
                CASE
                    WHEN created_at_epoch_millis > ${Long.MAX_VALUE - DEFAULT_NOTIFICATION_RETENTION_MILLIS}
                    THEN ${Long.MAX_VALUE}
                    ELSE created_at_epoch_millis + $DEFAULT_NOTIFICATION_RETENTION_MILLIS
                END
            WHERE source_kind = 'NOTIFICATION'
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS
                index_triage_items_source_kind_retention_expires_at_epoch_millis
            ON triage_items (source_kind, retention_expires_at_epoch_millis)
            """.trimIndent(),
        )
    }
}
