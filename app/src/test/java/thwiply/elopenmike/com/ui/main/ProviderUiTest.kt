package thwiply.elopenmike.com.ui.main

import org.junit.Assert.assertEquals
import org.junit.Test
import thwiply.elopenmike.com.R
import thwiply.elopenmike.com.llm.provider.ModelProvider
import thwiply.elopenmike.com.llm.provider.ProviderReadiness

class ProviderUiTest {
    @Test fun `checking copy is shared and names only the selected provider`() {
        val checking = ProviderReadiness.Checking
        assertEquals(R.string.provider_loading, checking.message(null, busy = true))
        assertEquals(R.string.qwen_metadata_checking, checking.message(ModelProvider.QWEN, busy = true))
        assertEquals(R.string.nano_checking, checking.message(ModelProvider.GEMINI_NANO, busy = true))
        assertEquals(R.string.nano_check_needed, checking.message(ModelProvider.GEMINI_NANO, busy = false))
    }
}
