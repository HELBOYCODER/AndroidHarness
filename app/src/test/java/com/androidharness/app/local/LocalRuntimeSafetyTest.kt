package com.androidharness.app.local

import org.junit.Assert.*
import org.junit.Test

class LocalRuntimeSafetyTest {
    private val model = LocalModelCatalog.find("qwen-05b")!!
    private val device = LocalDeviceProfile(4 * GIB, 3 * GIB, 8 * GIB, "arm64-v8a", 8, false)

    @Test fun `normal small model passes without mutating limits`() {
        val limits = LocalModelLimits()
        LocalRuntimeSafety.requireCanRun(model, limits, device)
        assertEquals(2048, limits.context)
    }

    @Test fun `unsafe dimensions fail before JNI`() {
        listOf(LocalModelLimits(context = Int.MAX_VALUE), LocalModelLimits(input = Int.MAX_VALUE),
            LocalModelLimits(output = 131073), LocalModelLimits(threads = 129),
            LocalModelLimits(promptThreads = Int.MAX_VALUE)).forEach {
            assertThrows(IllegalArgumentException::class.java) { it.validate() }
        }
        LocalModelLimits(context = 131072, input = 98304, output = 32768, threads = 32).validate()
    }

    @Test fun `unsupported ABI low memory and insufficient live RAM fail`() {
        listOf(device.copy(abi = "armeabi-v7a"), device.copy(lowMemory = true),
            device.copy(availableRam = GIB / 2), device.copy(totalRam = GIB / 2)).forEach {
            assertThrows(IllegalStateException::class.java) {
                LocalRuntimeSafety.requireCanRun(model, LocalModelLimits(), it)
            }
        }
    }

    @Test fun `resident model weights are not counted twice`() {
        val constrained = device.copy(availableRam = 1100L * 1024 * 1024)
        assertThrows(IllegalStateException::class.java) {
            LocalRuntimeSafety.requireCanRun(model, LocalModelLimits(), constrained)
        }
        LocalRuntimeSafety.requireCanRun(model, LocalModelLimits(), constrained, weightsResident = true)
    }

    @Test fun `malformed metadata cannot overflow memory admission`() {
        val invalid = model.copy(bytes = Long.MAX_VALUE, kvBytesPerToken = Long.MAX_VALUE)
        assertEquals(Long.MAX_VALUE, invalid.estimatedMemory(2048))
        assertEquals(Long.MAX_VALUE, model.copy(kvBytesPerToken = -1).estimatedMemory(2048))
        assertThrows(IllegalStateException::class.java) {
            LocalRuntimeSafety.requireCanRun(invalid, LocalModelLimits(), device)
        }
    }

    @Test fun `new compact creator labeled models use pinned verified GGUF files`() {
        assertEquals(5, LocalUncensoredModels.models.size)
        LocalUncensoredModels.models.forEach {
            assertTrue(it.abliterated)
            assertEquals(LocalModelFormat.GGUF, it.format)
            assertTrue(it.bytes in 1..2_000_000_000L)
            assertTrue(it.revision.matches(Regex("[0-9a-f]{40}")))
            assertTrue(it.sha256.matches(Regex("[0-9a-f]{64}")))
            assertTrue(it.filename.endsWith("Q4_K_M.gguf"))
            assertEquals(it, LocalModelCatalog.find(it.id))
            assertTrue(it.description.contains("var", ignoreCase = true) || it.description.contains("guarantee"))
        }
    }
}
