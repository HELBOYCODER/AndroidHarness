package com.androidharness.app.local

/** HF API + ranged GGUF metadata checked 2026-11-05; all files have chat templates.
 * qwen2/qwen3/llama are supported by pinned llama.cpp 19cce6919992fdffd92e0ac152caaf1e2b8f254e.
 * “Uncensored”/“abliterated” describes the creator's label, not guaranteed behavior or quality.
 */
internal object LocalUncensoredModels {
    val models = listOf(
        LocalModelSpec("qwen25-05b-unc", "Qwen 2.5 0.5B Uncensored", "mradermacher/Qwen2.5-0.5B-Instruct-uncensored-GGUF",
            "b8b5ec73d7df2f9cc6499a38f5ad96963bbc669b", "Qwen2.5-0.5B-Instruct-uncensored.Q4_K_M.gguf", 491400544L,
            "994e93c26249d47c998a55065d77a6f037aa3d10b7db106126347f938ffff5e3", 2, 24576L,
            "Creator-labeled uncensored chat variant. Reduced refusals are not guaranteed; small-model quality varies.",
            license = "See model source for license terms", maxContext = 32768, abliterated = true),
        LocalModelSpec("qwen3-06b-unc", "Qwen 3 0.6B Uncensored", "mradermacher/Qwen3-0.6B-Uncensored-GGUF",
            "bd6bc01ac63854456349f3307ee4eea513dfba2b", "Qwen3-0.6B-Uncensored.Q4_K_M.gguf", 396705440L,
            "47973d7e479ae0d056b4ceb01519d66b271281574bcad185016352fecd079fdb", 3, 114688L,
            "Creator-labeled uncensored Qwen 3 variant with optional thinking. Refusal behavior and answer quality vary.",
            license = "mit (repository label; also review base-model terms)", maxContext = 40960, abliterated = true),
        LocalModelSpec("llama32-1b-abl", "Llama 3.2 1B Abliterated", "mradermacher/Llama-3.2-1B-Instruct-abliterated-GGUF",
            "becf36858832feddfea0ace2fabf791797571999", "Llama-3.2-1B-Instruct-abliterated.Q4_K_M.gguf", 955445792L,
            "68336c25367576f49b29f01ee9034658b51bbded6a7f215e934d997de96301e0", 3, 32768L,
            "Community abliterated Llama chat variant. Creator's label does not guarantee unrestricted answers or accuracy.",
            license = "llama3.2", maxContext = 131072, abliterated = true),
        LocalModelSpec("qwen25-15b-unc", "Qwen 2.5 1.5B Uncensored", "mradermacher/Qwen2.5-1.5B-Instruct-uncensored-GGUF",
            "ecee937554b3b66f89e7eb4fc432b8d8445208e7", "Qwen2.5-1.5B-Instruct-uncensored.Q4_K_M.gguf", 1117321536L,
            "d63b6a7f90ada2b619e3cd87be4e10fe517d22d7eb420287490fd67c51c4bda8", 4, 28672L,
            "Creator-labeled uncensored chat variant. Reduced refusals are not guaranteed; tool reliability varies.",
            license = "See model source for license terms", maxContext = 32768, abliterated = true),
        LocalModelSpec("qwen25-3b-unc", "Qwen 2.5 3B Uncensored Test", "mradermacher/Qwen2.5-3B-Instruct-Uncensored-Test-GGUF",
            "44c1e26427ca24f32d0ffd3fe002a79621170015", "Qwen2.5-3B-Instruct-Uncensored-Test.Q4_K_M.gguf", 1929903616L,
            "e1c009811b547164f15dce81d3da5db36023f28179ecd44f8ff196febe6e2489", 6, 36864L,
            "Experimental creator-labeled uncensored variant. Under 2 GB on disk, but needs substantially more RAM; quality varies.",
            license = "apache-2.0 (repository label; also review base-model terms)", maxContext = 32768, abliterated = true),
    )
}
