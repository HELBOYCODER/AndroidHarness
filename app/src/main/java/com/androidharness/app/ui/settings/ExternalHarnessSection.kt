package com.androidharness.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidharness.app.AppContainer
import com.androidharness.app.R
import com.androidharness.app.harness.ExternalHarness
import com.androidharness.app.harness.ExternalHarnessBridge
import com.androidharness.app.harness.HarnessCommand
import com.androidharness.app.tools.SecretRedactor
import com.androidharness.app.ui.common.OutlinedButton
import com.androidharness.app.ui.common.TextButton
import com.androidharness.app.ui.common.Button
import com.androidharness.app.ui.common.AppLinearProgressIndicator
import com.androidharness.app.workspace.SshFs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun ExternalHarnessSection(container: AppContainer) {
    val workspace by container.workspace.current.collectAsStateWithLifecycle(initialValue = null)
    val remote = workspace as? SshFs
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(ExternalHarness.CLAUDE_CODE) }
    // ponytail: prompts/results are deliberately transient, not saved to backups, chat or instance state.
    var prompt by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(false) }
    var output by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<Job?>(null) }
    var busy by remember { mutableStateOf(false) }
    var uncertain by remember { mutableStateOf(ExternalHarnessBridge.outcomeUncertain) }
    LaunchedEffect(workspace?.displayPath, selected) {
        job?.cancel()
        consent = false
        output = ""
    }
    fun start(review: Boolean) {
        val target = remote ?: return
        if (busy || selected.executable == null) return
        val chosen = selected
        val request = if (review) prompt else null
        busy = true
        output = ""
        job = scope.launch {
            try {
                output = ExternalHarnessBridge.execute(target, chosen, request)
            } catch (e: CancellationException) {
                output = context.getString(R.string.external_harness_uncertain)
                throw e
            } catch (e: Exception) {
                output = context.getString(R.string.external_harness_error, SecretRedactor.redact(e.message ?: e.javaClass.simpleName))
            } finally {
                uncertain = ExternalHarnessBridge.outcomeUncertain
                busy = false
            }
        }
    }
    SettingsPanel(Modifier.fillMaxWidth().settingsAnchor(R.string.external_harness_title)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.external_harness_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.external_harness_intro), style = MaterialTheme.typography.bodySmall)
            ExternalHarness.entries.forEach { harness ->
                val label = stringResource(when (harness) {
                    ExternalHarness.CLAUDE_CODE -> R.string.external_harness_claude
                    ExternalHarness.CODEX -> R.string.external_harness_codex
                    ExternalHarness.PI -> R.string.external_harness_pi
                    ExternalHarness.DEEPSEA -> R.string.external_harness_deepsea
                })
                OutlinedButton(onClick = { selected = harness }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    RadioButton(selected = selected == harness, onClick = null)
                    Spacer(Modifier.width(8.dp))
                    Text(label)
                }
            }
            if (selected == ExternalHarness.DEEPSEA) {
                Text(stringResource(R.string.external_harness_deepsea_unknown), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                TextButton(onClick = { runCatching { uri.openUri(selected.documentation) } }) {
                    Text(stringResource(R.string.external_harness_docs))
                }
                Text(stringResource(R.string.external_harness_ssh_required), style = MaterialTheme.typography.bodySmall)
                remote?.let {
                    Text(stringResource(R.string.external_harness_target, it.displayPath), style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(onClick = { start(false) }, enabled = remote != null && !busy && !uncertain) {
                    Text(stringResource(R.string.external_harness_check))
                }
                OutlinedTextField(value = prompt,
                    onValueChange = { if (it.length <= HarnessCommand.MAX_PROMPT_CHARS) prompt = it },
                    label = { Text(stringResource(R.string.external_harness_prompt)) },
                    placeholder = { Text(stringResource(R.string.external_harness_prompt_hint)) },
                    minLines = 3, maxLines = 8, enabled = !busy, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.external_harness_limit), style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth().toggleable(value = consent, enabled = !busy, role = Role.Checkbox, onValueChange = { consent = it })) {
                    Checkbox(checked = consent, onCheckedChange = null, enabled = !busy)
                    Text(stringResource(R.string.external_harness_trust), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
                }
                Button(onClick = { start(true) }, enabled = remote != null && consent && prompt.isNotBlank() && !busy && !uncertain) {
                    Text(stringResource(R.string.external_harness_run))
                }
            }
            Text(stringResource(R.string.external_harness_transient), style = MaterialTheme.typography.bodySmall)
            if (uncertain && !busy) {
                Text(stringResource(R.string.external_harness_uncertain), color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = {
                    runCatching { ExternalHarnessBridge.acknowledgeHostInspected() }
                        .onSuccess { uncertain = false }
                        .onFailure { output = SecretRedactor.redact(it.message.orEmpty()) }
                }) { Text(stringResource(R.string.external_harness_acknowledge)) }
            }
            if (busy) {
                AppLinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.external_harness_running), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { job?.cancel() }) { Text(stringResource(R.string.external_harness_cancel)) }
            }
            if (output.isNotBlank()) {
                SelectionContainer { Text(output, style = MaterialTheme.typography.bodySmall) }
                TextButton(onClick = { output = "" }, enabled = !busy) { Text(stringResource(R.string.external_harness_clear)) }
            }
        }
    }
}
