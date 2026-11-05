package com.androidharness.app.harness

import org.junit.Assert.*
import org.junit.Test

class HarnessCommandTest {
    @Test fun `all supported agents use stdin and never bypass permissions`() {
        ExternalHarness.entries.filter { it.executable != null }.forEach { harness ->
            val command = HarnessCommand.review(harness, "--help")
            assertTrue(command.startsWith("printf '%s' '--help' | "))
            assertFalse(command.contains("dangerously"))
            assertFalse(command.contains("bypass"))
            assertFalse(command.contains("--full-auto"))
        }
    }
    @Test fun `review tools are explicitly restricted`() {
        val claude = HarnessCommand.review(ExternalHarness.CLAUDE_CODE, "Review")
        assertTrue(claude.contains("--permission-mode plan"))
        assertTrue(claude.contains("--tools Read,Grep,Glob"))
        assertTrue(claude.contains("--strict-mcp-config"))
        val codex = HarnessCommand.review(ExternalHarness.CODEX, "Review")
        assertTrue(codex.contains("--sandbox read-only"))
        assertTrue(codex.contains("--ask-for-approval never"))
        val pi = HarnessCommand.review(ExternalHarness.PI, "Review")
        assertTrue(pi.contains("--tools read,grep,find,ls"))
        assertTrue(pi.contains("--no-extensions --no-mcp"))
        assertTrue(pi.contains("--no-context-files"))
    }
    @Test fun `DeepSea is not silently replaced with DeepSeek`() {
        assertNull(ExternalHarness.DEEPSEA.executable)
        assertTrue(runCatching { HarnessCommand.probe(ExternalHarness.DEEPSEA) }.isFailure)
        assertTrue(runCatching { HarnessCommand.review(ExternalHarness.DEEPSEA, "Review") }.isFailure)
    }
    @Test fun `invalid requests fail before any remote operation`() {
        listOf("", " \n", "\u0000", "a".repeat(HarnessCommand.MAX_PROMPT_CHARS + 1)).forEach {
            assertTrue(runCatching { HarnessCommand.review(ExternalHarness.CODEX, it) }.isFailure)
        }
    }
    @Test fun `POSIX quoting closes and reopens embedded single quotes`() {
        assertEquals("'a'\\''b'", HarnessCommand.quote("a'b"))
        assertEquals("'\$(touch injected)'", HarnessCommand.quote("\$(touch injected)"))
    }
    @Test fun `version probe diagnoses missing CLI without installing anything`() {
        val command = HarnessCommand.probe(ExternalHarness.PI)
        assertTrue(command.contains("command -v pi"))
        assertTrue(command.contains("exit 127"))
        assertTrue(command.endsWith("pi --version"))
        assertFalse(command.contains("npm"))
        assertFalse(command.contains("curl"))
    }
}
