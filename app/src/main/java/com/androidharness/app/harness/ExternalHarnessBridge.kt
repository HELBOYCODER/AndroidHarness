package com.androidharness.app.harness

import com.androidharness.app.tools.SecretRedactor
import com.androidharness.app.workspace.SshFs
import com.androidharness.app.workspace.WorkspaceFs
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout

/** Uses the existing verified-host SSH connection; never installs software or transfers API keys. */
object ExternalHarnessBridge {
    private val gate = Mutex()
    @Volatile var outcomeUncertain: Boolean = false
        private set

    /** Explicit user acknowledgement only; never infer remote termination from a closed channel. */
    fun acknowledgeHostInspected() {
        check(gate.tryLock()) { "Wait for the current request to disconnect." }
        try { outcomeUncertain = false } finally { gate.unlock() }
    }

    suspend fun execute(workspace: WorkspaceFs, harness: ExternalHarness, prompt: String? = null): String {
        // ponytail: SSH-only until each upstream CLI is actually verified on Android.
        val remote = requireNotNull(workspace as? SshFs) { "Connect an SSH workspace first. Local Android CLI execution is not verified." }
        check(gate.tryLock()) { "Another external harness request is still running." }
        try {
            check(!outcomeUncertain) { "Previous remote outcome is uncertain. Inspect the SSH host and acknowledge that no request is running before retrying." }
            val command = if (prompt == null) HarnessCommand.probe(harness) else HarnessCommand.review(harness, prompt)
            val timeout = if (prompt == null) 15_000 else HarnessCommand.TIMEOUT_MS
            // ponytail: keep a logical global safety gate after disconnect; SSH cannot prove process termination.
            val result = try {
                withTimeout(timeout.toLong() + 15_000L) {
                    remote.run(command, timeoutMs = timeout, maxOutput = HarnessCommand.MAX_OUTPUT_CHARS)
                }
            } catch (e: Exception) {
                outcomeUncertain = true
                throw e
            }
            if (result.timedOut || result.exitCode < 0) outcomeUncertain = true
            val report = buildString {
                append("exit code: ").append(result.exitCode).append('\n')
                if (result.timedOut) append("SSH request timed out; remote outcome is uncertain. Inspect the host before retrying.\n")
                result.note?.let { append(it).append('\n') }
                if (result.rawOutput.isNotBlank()) append(result.rawOutput).append('\n')
                if (result.rawStderr.isNotBlank()) append("stderr:\n").append(result.rawStderr)
                if (prompt == null && result.exitCode == 0) append("\nVersion check only: credentials and model availability are verified by the actual review request.")
            }
            // No raw credential values or complete provider settings are requested from the host.
            val safe = SecretRedactor.redact(report)
            val marker = "\n[display truncated]"
            return if (safe.length <= HarnessCommand.MAX_OUTPUT_CHARS) safe
            else safe.take(HarnessCommand.MAX_OUTPUT_CHARS - marker.length) + marker
        } finally {
            gate.unlock()
        }
    }
}
