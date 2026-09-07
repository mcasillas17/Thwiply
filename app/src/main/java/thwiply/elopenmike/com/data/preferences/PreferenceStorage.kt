package thwiply.elopenmike.com.data.preferences

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.StandardCopyOption

internal interface PreferenceStorage {
    suspend fun read(): AppPreferences
    suspend fun write(value: AppPreferences)
}

internal class PreferenceFormatException(val reason: PreferenceFailureReason) :
    IOException("Invalid app preference format: $reason")

internal class FilePreferenceStorage(private val file: File) : PreferenceStorage {
    override suspend fun read(): AppPreferences {
        val bytes = try {
            Files.newInputStream(file.toPath()).use { input ->
                val buffer = ByteArray(257)
                var length = 0
                while (length < buffer.size) {
                    val count = input.read(buffer, length, buffer.size - length)
                    if (count == -1) break
                    length += count
                }
                if (length > 256) malformed()
                buffer.copyOf(length)
            }
        } catch (_: NoSuchFileException) {
            return AppPreferences()
        }
        if (bytes.any { it < 0 }) malformed()
        val lines = bytes.toString(Charsets.US_ASCII).split('\n')
        if (lines.size != 4 || lines[3].isNotEmpty() ||
            !lines[0].startsWith("version=") || !lines[1].startsWith("theme=") ||
            !lines[2].startsWith("setupEducation=")) malformed()
        val schemaText = lines[0].removePrefix("version=")
        val schemaVersion = schemaText.toIntOrNull()?.takeIf { it > 0 && it.toString() == schemaText }
            ?: malformed()
        if (schemaVersion != 1) throw PreferenceFormatException(PreferenceFailureReason.UNSUPPORTED)
        val theme = ThemeMode.entries.firstOrNull { "theme=${it.storageId}" == lines[1] }
            ?: throw PreferenceFormatException(PreferenceFailureReason.UNSUPPORTED)
        val versionText = lines[2].removePrefix("setupEducation=")
        val educationVersion = versionText.toIntOrNull()?.takeIf { it >= 0 && it.toString() == versionText }
            ?: malformed()
        return AppPreferences(theme, educationVersion)
    }

    override suspend fun write(value: AppPreferences) {
        val parent = requireNotNull(file.absoluteFile.parentFile).toPath()
        Files.createDirectories(parent)
        // The singleton repository serializes writes; reuse one candidate after process death.
        val candidate = parent.resolve("${file.name}.tmp")
        var primary: Throwable? = null
        try {
            FileOutputStream(candidate.toFile()).use {
                it.write(
                    "version=1\ntheme=${value.theme.storageId}\nsetupEducation=${value.modelSetupEducationVersion}\n"
                        .toByteArray(Charsets.US_ASCII),
                )
                it.fd.sync()
            }
            Files.move(candidate, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (failure: Throwable) {
            primary = failure
            throw failure
        } finally {
            try {
                Files.deleteIfExists(candidate)
            } catch (cleanup: Exception) {
                if (primary != null) primary.addSuppressed(cleanup) else throw cleanup
            }
        }
    }

    private fun malformed(): Nothing = throw PreferenceFormatException(PreferenceFailureReason.MALFORMED)
}
