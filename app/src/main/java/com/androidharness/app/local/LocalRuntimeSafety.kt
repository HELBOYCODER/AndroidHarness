package com.androidharness.app.local

/** Last-moment admission checks, not a guarantee against Android's low-memory killer. */
object LocalRuntimeSafety {
    const val MAX_CONTEXT = 131072
    const val MAX_THREADS = 128
    private const val RESERVE = 256L * 1024 * 1024

    fun requireCanRun(model: LocalModelSpec, limits: LocalModelLimits, device: LocalDeviceProfile,
        weightsResident: Boolean = false) {
        limits.validate()
        check(device.supported) { "Local inference requires a 64-bit ARM or x86 engine." }
        check(!device.lowMemory) { "Android reports low memory. Close other apps or choose a smaller model before retrying." }
        val estimate = model.estimatedMemory(limits.context)
        check(estimate <= (device.totalRam - RESERVE).coerceAtLeast(0)) {
            "This model and context exceed physical RAM. Choose a smaller model or lower the context."
        }
        // mmap weights that are already resident must not be counted twice. Context and
        // working buffers remain conservative estimates, including when reusing a KV cache.
        val additional = if (weightsResident) (estimate - model.bytes).coerceAtLeast(0) else estimate
        check(additional <= (device.availableRam - RESERVE).coerceAtLeast(0)) {
            "Not enough available RAM for this model and context. Unload other models, close apps, or lower the context."
        }
    }
}
