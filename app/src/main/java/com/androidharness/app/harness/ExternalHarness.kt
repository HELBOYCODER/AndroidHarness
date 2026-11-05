package com.androidharness.app.harness

/** External coding agents are executables, not interchangeable model API providers. */
enum class ExternalHarness(val executable: String?, val documentation: String) {
    CLAUDE_CODE("claude", "https://code.claude.com/docs/en/overview"),
    CODEX("codex", "https://developers.openai.com/codex/cli/"),
    PI("pi", "https://github.com/badlogic/pi-mono/tree/main/packages/coding-agent"),
    // ponytail: DeepSea has no verified identity/protocol; never alias it to DeepSeek.
    DEEPSEA(null, ""),
}

object HarnessCommand {
    const val MAX_PROMPT_CHARS = 16_000
    const val TIMEOUT_MS = 600_000
    const val MAX_OUTPUT_CHARS = 60_000

    /** POSIX shell quoting. Prompts always travel on stdin, never as CLI options or @files. */
    internal fun quote(value: String): String {
        require('\u0000' !in value) { "NUL is not valid shell input." }
        return "'" + value.replace("'", "'\\''") + "'"
    }

    fun probe(harness: ExternalHarness): String {
        val executable = requireNotNull(harness.executable) { "DeepSea is not configured: its project and protocol are unverified." }
        return "command -v $executable >/dev/null 2>&1 || { printf '%s\\n' 'Executable not found on SSH host PATH; install and authenticate it there first.' >&2; exit 127; }; $executable --version"
    }

    fun review(harness: ExternalHarness, prompt: String): String {
        require(prompt.isNotBlank()) { "Enter a review request." }
        require(prompt.length <= MAX_PROMPT_CHARS) { "Review request exceeds $MAX_PROMPT_CHARS characters." }
        // ponytail: one-shot review only; no permission bypass, edit mode, SDK/session emulation, or local Android CLI claim.
        val invocation = when (harness) {
            ExternalHarness.CLAUDE_CODE -> "claude --print --output-format text --permission-mode plan " +
                "--tools Read,Grep,Glob --strict-mcp-config --mcp-config " + quote("{\"mcpServers\":{}}") +
                " --setting-sources '' --disable-slash-commands --no-session-persistence"
            ExternalHarness.CODEX -> "codex --ask-for-approval never exec --sandbox read-only --skip-git-repo-check --color never -"
            ExternalHarness.PI -> "pi --print --tools read,grep,find,ls --no-extensions --no-mcp " +
                "--no-skills --no-prompt-templates --no-themes --no-context-files --no-session"
            ExternalHarness.DEEPSEA -> error("DeepSea is not configured: its project and protocol are unverified.")
        }
        return "printf '%s' ${quote(prompt)} | $invocation"
    }
}
