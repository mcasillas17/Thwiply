package thwiply.elopenmike.com.llm.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelPresetTest {
    @Test
    fun `release presets contain immutable verification metadata`() {
        assertFalse(ModelPreset.PRESETS.isEmpty())

        ModelPreset.PRESETS.forEach { preset ->
            assertTrue(preset.fileName.endsWith(".litertlm"))
            assertTrue(preset.expectedBytes > 0)
            assertTrue(preset.sha256.matches(Regex("[0-9a-f]{64}")))
            assertTrue(preset.url.startsWith("https://huggingface.co/"))
            assertFalse(preset.url.contains("/resolve/main/"))
        }
    }

    @Test
    fun `release presets only expose the vetted qwen model`() {
        assertEquals(listOf("qwen-2.5-1.5b"), ModelPreset.PRESETS.map { it.id })
    }
}
