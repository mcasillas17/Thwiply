package thwiply.elopenmike.com.data.preferences

enum class ThemeMode(val storageId: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
}

// Increment when the meaning of setup_optional_description changes.
const val MODEL_SETUP_EDUCATION_VERSION = 1

/** App-owned display preferences only; education acknowledgement is never permission or consent. */
data class AppPreferences(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val modelSetupEducationVersion: Int = 0,
) {
    init { require(modelSetupEducationVersion >= 0) }

    val hasSeenModelSetupEducation: Boolean
        get() = modelSetupEducationVersion == MODEL_SETUP_EDUCATION_VERSION
}

enum class PreferenceFailureReason { READ, MALFORMED, UNSUPPORTED, WRITE, RESET }

data class PreferenceFailure(val reason: PreferenceFailureReason, val cause: Throwable)

data class PreferenceState(
    val values: AppPreferences? = null,
    val failure: PreferenceFailure? = null,
) {
    val canUpdate: Boolean
        get() = values != null && (failure == null || failure.reason == PreferenceFailureReason.WRITE)
}
