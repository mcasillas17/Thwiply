package thwiply.elopenmike.com.llm.model

data class ModelPreset(
    val id: String,
    val name: String,
    val tag: String,
    val size: String,
    val description: String,
    val url: String,
    val fileName: String,
    val expectedBytes: Long,
    val sha256: String
) {
    companion object {
        val QWEN_2_5_1_5B = ModelPreset(
            id = "qwen-2.5-1.5b",
            name = "Qwen 2.5 1.5B Instruct",
            tag = "Verified • No account needed",
            size = "1.49 GiB",
            description = "A fixed LiteRT-LM build verified before it becomes active.",
            url = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/19edb84c69a0212f29a6ef17ba0d6f278b6a1614/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm?download=true",
            fileName = "qwen-2.5-1.5b-q8-ekv4096.litertlm",
            expectedBytes = 1_597_931_520L,
            sha256 = "faa60663b333290c1496c499828b21d3e3254a788cacd8cce917ce0f761a2dc9"
        )

        val PRESETS = listOf(QWEN_2_5_1_5B)

        fun findById(id: String): ModelPreset? = PRESETS.firstOrNull { it.id == id }
    }
}
