package com.androidharness.app.harness

import java.io.File
import java.nio.file.Files

/** Network-free contract tests; fake CLIs verify the real generated POSIX command, not provider behavior. */
fun main() {
    val root = Files.createTempDirectory("harness-smoke-").toFile()
    var assertions = 0
    fun verify(value: Boolean, message: String) { check(value) { message }; assertions++ }
    try {
        val input = File(root, "stdin")
        val argsFile = File(root, "args")
        val marker = File(root, "injected")
        val script = "#!/bin/sh\nprintf '%s\\n' \"\$@\" > \"\$HARNESS_ARGS\"\ncat > \"\$HARNESS_INPUT\"\nexit \"\${HARNESS_EXIT:-0}\"\n"
        listOf("claude", "codex", "pi").forEach { File(root, it).apply { writeText(script); setExecutable(true) } }
        fun run(command: String, code: Int = 0, missing: Boolean = false): Pair<Int, String> {
            val builder = ProcessBuilder("/bin/sh", "-c", command).redirectErrorStream(true)
            builder.environment().putAll(mapOf("PATH" to if (missing) root.resolve("missing").path else root.path + ":/usr/bin:/bin",
                "HARNESS_ARGS" to argsFile.path, "HARNESS_INPUT" to input.path, "HARNESS_EXIT" to code.toString()))
            val process = builder.start()
            val text = process.inputStream.bufferedReader().readText()
            return process.waitFor() to text
        }
        val prompts = listOf("Review code", "' ; touch ${marker.path}; #", "\$(touch ${marker.path}) `touch ${marker.path}`",
            "--help", "@/etc/passwd", "line one\nline two\n", "بازبینی کد و نویسهٔ ‘نقل‌قول’", "a\\b\t%q")
        ExternalHarness.entries.filter { it.executable != null }.forEach { harness ->
            prompts.forEach { prompt ->
                verify(run(HarnessCommand.review(harness, prompt)).first == 0, "command failed: $harness")
                verify(input.readText() == prompt, "stdin changed: $harness")
                verify(!marker.exists(), "shell injection: $harness")
                verify(prompt !in argsFile.readLines(), "prompt leaked into CLI arguments: $harness")
            }
            verify(run(HarnessCommand.review(harness, "test"), 23).first == 23, "CLI error exit lost")
            val probe = run(HarnessCommand.probe(harness), missing = true)
            verify(probe.first == 127 && probe.second.contains("Executable not found"), "missing CLI diagnostics")
            val command = HarnessCommand.review(harness, "test")
            verify(!command.contains("bypass") && !command.contains("full-auto") && !command.contains("dangerously"), "unsafe mode")
            verify(runCatching { HarnessCommand.review(harness, " ") }.isFailure, "blank request accepted")
            verify(runCatching { HarnessCommand.review(harness, "\u0000") }.isFailure, "NUL accepted")
            verify(runCatching { HarnessCommand.review(harness, "x".repeat(HarnessCommand.MAX_PROMPT_CHARS + 1)) }.isFailure, "oversize accepted")
        }
        verify(HarnessCommand.review(ExternalHarness.CODEX, "test").contains("--sandbox read-only"), "Codex must be read-only")
        verify(HarnessCommand.review(ExternalHarness.CLAUDE_CODE, "test").contains("--permission-mode plan"), "Claude must be plan-only")
        verify(HarnessCommand.review(ExternalHarness.PI, "test").contains("--no-extensions --no-mcp"), "Pi extension isolation missing")
        verify(ExternalHarness.DEEPSEA.executable == null, "DeepSea must remain unverified")
        verify(runCatching { HarnessCommand.probe(ExternalHarness.DEEPSEA) }.isFailure, "DeepSea probe enabled")
        verify(runCatching { HarnessCommand.review(ExternalHarness.DEEPSEA, "test") }.isFailure, "DeepSea execution enabled")
        println("PASS: $assertions assertions; stdin integrity, injection resistance, limits, missing CLI, CLI failure, review flags, DeepSea gating")
    } finally { root.deleteRecursively() }
}
