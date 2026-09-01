package thwiply.elopenmike.com

import android.content.pm.ApplicationInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

@RunWith(AndroidJUnit4::class)
class BackupConfigurationTest {
    @Test
    fun roomDataIsExcludedFromCloudBackupAndDeviceTransfer() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val applicationInfo = context.applicationInfo
        assertEquals(0, applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)

        val databaseExclusions = mutableSetOf<String>()
        context.resources.getXml(R.xml.data_extraction_rules).use { parser ->
            var section: String? = null
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "cloud-backup", "device-transfer" -> section = parser.name
                        "exclude" -> if (
                            parser.getAttributeValue(null, "domain") == "database" &&
                            parser.getAttributeValue(null, "path") == "."
                        ) {
                            section?.let(databaseExclusions::add)
                        }
                    }
                } else if (
                    parser.eventType == XmlPullParser.END_TAG &&
                    parser.name == section
                ) {
                    section = null
                }
                parser.next()
            }
        }

        assertEquals(setOf("cloud-backup", "device-transfer"), databaseExclusions)

        var legacyDatabaseExcluded = false
        context.resources.getXml(R.xml.backup_rules).use { parser ->
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (
                    parser.eventType == XmlPullParser.START_TAG &&
                    parser.name == "exclude" &&
                    parser.getAttributeValue(null, "domain") == "database" &&
                    parser.getAttributeValue(null, "path") == "."
                ) {
                    legacyDatabaseExcluded = true
                }
                parser.next()
            }
        }
        assertTrue(legacyDatabaseExcluded)
    }
}
