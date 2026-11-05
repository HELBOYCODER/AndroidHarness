# External coding harnesses

## What is integrated

Settings → Coding → **External harnesses** connects the existing SSH workspace
transport to actual installed **Claude Code**, **Codex Harness** (OpenAI Codex
CLI), and **Pi Harness** (badlogic/pi coding agent) executables. Settings search
also finds these names. English and Persian copy are included.

This is a deliberately bounded, one-shot **review** interface. It is not a new
model provider, a replacement for the built-in Code engine, an ACP/RPC session,
or an implementation of the vendors' entire interactive applications. Results
are shown as selectable temporary text in Settings, not silently inserted into
chat or consumed by a second model. No background service or automatic restart
is added.

**DeepSea Harness is unconfigured.** The requested name did not identify a
verified upstream repository, CLI, API or protocol. It is visible with an honest
explanation, cannot execute, and is **not** silently substituted with DeepSeek.
DeepSeek's model API is a different concept from an external coding harness.

## Setup and use

1. Prepare a trusted Linux/macOS host, VM, or a compatible environment you manage.
   Use an isolated account containing only projects you authorize the agent to
   read. Follow the selected CLI's official installation instructions:
   - Claude Code: https://code.claude.com/docs/en/overview
   - Codex: https://developers.openai.com/codex/cli/
   - Pi: https://github.com/badlogic/pi-mono/tree/main/packages/coding-agent
2. Authenticate **on that host**, using the CLI's supported login/key mechanism.
   The app never copies provider API keys, OAuth sessions, or local credentials
   into the external CLI. Paid model access may be required. Ensure `claude`,
   `codex`, or `pi` is available on the non-interactive SSH shell's `PATH`.
3. Add/select an SSH workspace through the app's existing Workspace settings.
   Existing host-key/fingerprint checks and SSH connection credentials remain in
   effect. The selected remote workspace root is the CLI working directory.
4. Open Settings → Coding → External harnesses, select the CLI, and choose
   **Check CLI version**. This executes `command -v` and `--version`, not a fake
   installed flag. A successful version check does **not** establish that the
   host is authenticated or that its configured model is available.
5. Enter a request, read the trust warning, opt in, and choose **Run review**.
   The real CLI performs its provider call using its host configuration. Output,
   stderr, exit status, authentication errors, unsupported options and missing
   runtime errors are reported rather than replaced with simulated replies.
6. Use **Disconnect request** if needed. Disconnecting, timing out, changing
   workspace, or leaving the page closes the client request; an SSH disconnect
   cannot guarantee that the remote process exits. Inspect the host before
   retrying, especially after a connection failure. Results are temporary; copy
   what you need before leaving.

Local Android execution is intentionally disabled: installing Node or having a
shell does not verify each upstream CLI's native binaries, sandbox, terminal,
credential flow and Android compatibility. No claim of on-device CLI support is
made. The existing model providers and Code engine continue to work as before.

## Command contract

Prompts are POSIX-single-quoted and piped through `printf '%s'` to **stdin**.
They never become CLI options, positional `@file` references, shell expansions,
file paths, or an interpolated executable. Executables/options are a fixed enum;
there is no arbitrary-command field and no automatic `npm`, `curl`, or install
step. New runs do not resume an old external session.

- **Claude Code:** print/text output, plan permission mode, only `Read,Grep,Glob`,
  strict empty MCP config, empty user/project setting sources, slash commands
  disabled, and no session persistence.
- **Codex:** `exec`, read-only sandbox, approval policy `never` (deny commands
  needing elevation; **not** bypass permissions), plain text, stdin prompt, and
  permission to review a non-Git workspace. It does not use `--full-auto` or any
  dangerous bypass flag. Codex may still write its own CLI state on the host.
- **Pi:** print mode, only `read,grep,find,ls`; no extensions, MCP, skills,
  prompt templates, themes or discovered context files; no persisted session.

These options target current documented CLI interfaces. Older installed versions
may reject an option and must be updated by the host administrator. Such errors
are displayed without retrying with weaker permissions.

## Security boundary and limits

**Review flags are not an OS security boundary.** The app's per-tool approval,
workspace ignore rules and shell denylist do not wrap the external agent's
internal actions. In particular, installed CLI startup code, host policy, global
hooks/plugins, or Codex MCP configuration may have permissions beyond this
review UI. A tool allowlist is not a guarantee that files outside the project
cannot be read. Use a dedicated, restricted host account/container and trust its
configuration. Do not select an account with unrelated secrets. The trust
checkbox explicitly authorizes transmission of project content to the CLI's
model provider and resets when the selected harness or workspace changes.

No raw credentials are requested or logged. Common secret shapes in results and
errors are scrubbed with the existing `SecretRedactor` before display, but
pattern-based redaction cannot recognize every possible secret. Avoid putting
secrets into prompts or project content. Prompts/results are Compose `remember`
state only, not saved instance state, backups, provider configuration or chat.

Only one bridge request runs at a time. Requests are limited to 16,000 characters,
10 minutes for reviews / 15 seconds for version checks, and 60,000 output
bytes per stream with a 60,000-character display cap and truncation marker. Output is buffered by
the existing SSH transport; this is not a streaming terminal, interactive login,
resume workflow, or file-edit integration. Cancelled remote outcomes remain
uncertain; no automatic rerun is performed. A process-wide logical safety gate
blocks all subsequent bridge requests after cancellation, timeout or an unknown
exit status until the user explicitly confirms that they inspected the host and
no harness request is running. This gate is in-memory, not a remote process
monitor; after an app restart the user must still inspect the host.

## Architecture rationale

The app already has Anthropic, OpenAI-compatible and Responses model transports,
Code agents in `harness.json`, MCP, shell tools and an SSH workspace. Model APIs
are not Claude Code/Codex/Pi executables. Reusing `SshFs.run`/`SshConnections.run`
gives this bounded feature the existing cwd isolation, verified host connection,
timeout, bounded stdout/stderr and cancellation without pretending to support
unverified native Android runtimes. No native, download or build file changes
are required. Simplifications are marked `// ponytail` in the implementation.

## Tests

Standard JVM tests (with the project's normal Android build prerequisites):

```sh
./gradlew :app:testDebugUnitTest --tests 'com.androidharness.app.harness.HarnessCommandTest'
```

Network-free standalone smoke test, with `kotlinc` on PATH:

```sh
sh scripts/external-harness-smoke/run.sh
```

Or reuse a Gradle distribution's bundled Kotlin compiler:

```sh
KOTLIN_COMPILER_LIB=/path/to/gradle/lib sh scripts/external-harness-smoke/run.sh
```

The smoke test executes actual generated POSIX commands against fake CLI
executables. It checks single quotes, command substitution, backticks,
newlines, Persian text, option-like prompts, `@file`-like prompts, exact stdin
preservation, no shell injection, missing executables, nonzero CLI exit codes,
request limits, review flags and DeepSea gating. This verifies the bridge
contract, **not** a paid provider call or vendor CLI Android compatibility.
Manual live validation requires a user-authorized SSH host with each genuine
CLI installed/authenticated. No such credentials or host were supplied during
implementation; no live vendor success is claimed.

Upstream references reviewed: Pi's `docs/cli.md` and coding-agent README,
OpenAI Codex `codex-rs/exec/src/cli.rs`, Claude Code's changelog and the official
Python Agent SDK's subprocess CLI transport. The UI retains the application's
existing Material/SettingsPanel tokens rather than introducing a new visual
style.
