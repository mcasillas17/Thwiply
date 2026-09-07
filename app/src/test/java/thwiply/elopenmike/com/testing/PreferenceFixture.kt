package thwiply.elopenmike.com.testing

import java.io.File
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import thwiply.elopenmike.com.data.preferences.AppPreferencesRepository
import thwiply.elopenmike.com.data.preferences.FilePreferenceStorage

internal fun TestScope.preferenceFixture(directory: File) = AppPreferencesRepository(
    FilePreferenceStorage(File(directory, "preferences")),
    backgroundScope,
    StandardTestDispatcher(testScheduler),
)
