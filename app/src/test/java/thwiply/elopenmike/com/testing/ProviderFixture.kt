package thwiply.elopenmike.com.testing

import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import okhttp3.OkHttpClient
import thwiply.elopenmike.com.llm.engine.LlmEngineManager
import thwiply.elopenmike.com.llm.model.ModelManager
import thwiply.elopenmike.com.llm.model.ModelPreset
import thwiply.elopenmike.com.llm.provider.*

@OptIn(ExperimentalCoroutinesApi::class)
internal suspend fun TestScope.providerFixture(
    directory: File,
    engine: LlmEngineManager = LlmEngineManager(StandardTestDispatcher(testScheduler)) {
        error("Native engine not expected")
    },
    models: ModelManager = ModelManager(File(directory, "models"), OkHttpClient(), ModelPreset.PRESETS),
    nanoFactory: NanoClientFactory = NanoClientFactory { error("Nano must not be used for Qwen") },
): InferenceCoordinator {
    val dispatcher = StandardTestDispatcher(testScheduler)
    models.awaitLoaded()
    val selection = ProviderSelectionRepository(File(directory, "provider"), backgroundScope, dispatcher)
    selection.awaitLoaded()
    val coordinator = InferenceCoordinator(selection, models, engine, nanoFactory, backgroundScope, dispatcher)
    coordinator.setForeground(true)
    runCurrent()
    return coordinator
}
