package thwiply.elopenmike.com.data.preferences

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import thwiply.elopenmike.com.di.ApplicationScope

@Singleton
class AppPreferencesRepository internal constructor(
    private val storage: PreferenceStorage,
    scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
) {
    @Inject constructor(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ) : this(FilePreferenceStorage(File(context.noBackupFilesDir, "app-preferences")), scope, Dispatchers.IO)

    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(PreferenceState())
    val state = mutableState.asStateFlow()
    private val initialRead = scope.async(dispatcher) { mutex.withLock { read() } }

    suspend fun reload(): Boolean {
        initialRead.await()
        return withContext(dispatcher) { mutex.withLock { read() } }
    }

    suspend fun setTheme(theme: ThemeMode): Boolean = update { it.copy(theme = theme) }

    suspend fun acknowledgeModelSetupEducation(): Boolean =
        update { it.copy(modelSetupEducationVersion = MODEL_SETUP_EDUCATION_VERSION) }

    /** Only call after explicit confirmation; does not reset providers, weights, tasks or permissions. */
    suspend fun resetPreferences(): Boolean = update(reset = true) { AppPreferences() }

    private suspend fun update(reset: Boolean = false, transform: (AppPreferences) -> AppPreferences): Boolean {
        initialRead.await()
        return withContext(dispatcher) {
            mutex.withLock {
                if (!reset && !mutableState.value.canUpdate) return@withLock false
                currentCoroutineContext().ensureActive()
                val next = transform(mutableState.value.values ?: AppPreferences())
                val failureReason = if (reset) PreferenceFailureReason.RESET else PreferenceFailureReason.WRITE
                // Once a bounded commit starts, cancellation cannot separate disk from published state.
                withContext(NonCancellable) {
                    try {
                        storage.write(next)
                        mutableState.value = PreferenceState(next)
                        true
                    } catch (failure: IOException) {
                        fail(failureReason, failure)
                    } catch (failure: SecurityException) {
                        fail(failureReason, failure)
                    }
                }
            }
        }
    }

    private suspend fun read(): Boolean = try {
        val values = storage.read()
        currentCoroutineContext().ensureActive()
        mutableState.value = PreferenceState(values)
        true
    } catch (failure: PreferenceFormatException) {
        fail(failure.reason, failure)
    } catch (failure: IOException) {
        fail(PreferenceFailureReason.READ, failure)
    } catch (failure: SecurityException) {
        fail(PreferenceFailureReason.READ, failure)
    }

    private fun fail(reason: PreferenceFailureReason, cause: Throwable): Boolean {
        mutableState.value = mutableState.value.copy(failure = PreferenceFailure(reason, cause))
        return false
    }
}
