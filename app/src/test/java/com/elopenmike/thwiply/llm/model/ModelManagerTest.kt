package com.elopenmike.thwiply.llm.model

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class ModelManagerTest {
    @Test
    fun `test initial state when model does not exist`() {
        val mockContextDir = File(System.getProperty("java.io.tmpdir"))
        val modelManager = ModelManager(mockContextDir, null)
        assertFalse(modelManager.isModelAvailable())
    }
}
