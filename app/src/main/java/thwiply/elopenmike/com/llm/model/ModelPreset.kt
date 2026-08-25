package thwiply.elopenmike.com.llm.model

data class ModelPreset(
    val id: String,
    val name: String,
    val tag: String,
    val size: String,
    val description: String,
    val url: String,
    val requiresHfToken: Boolean
) {
    companion object {
        val QWEN_2_5_1_5B = ModelPreset(
            id = "qwen-2.5-1.5b",
            name = "Qwen 2.5 1.5B Instruct",
            tag = "Default • No Token Needed",
            size = "~900 MB",
            description = "Best-in-class structured JSON extraction. Direct 1-click download with no account or token required.",
            url = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm?download=true",
            requiresHfToken = false
        )

        val GEMMA_3_1B = ModelPreset(
            id = "gemma-3-1b",
            name = "Gemma 3 1B IT (int4)",
            tag = "Google • HF Token Required",
            size = "~550 MB",
            description = "Google AI Edge optimized. Fast and lightweight, but requires a Hugging Face token.",
            url = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.litertlm?download=true",
            requiresHfToken = true
        )

        val CUSTOM = ModelPreset(
            id = "custom",
            name = "Custom Model URL",
            tag = "Advanced",
            size = "Varies",
            description = "Specify a direct HTTPS link to any compatible .litertlm model file.",
            url = "",
            requiresHfToken = false
        )

        val PRESETS = listOf(QWEN_2_5_1_5B, GEMMA_3_1B, CUSTOM)
    }
}
