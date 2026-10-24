package com.gps.wareply.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gps.wareply.ai.ReplyWriter
import com.gps.wareply.ai.ModelStore
import com.gps.wareply.data.Prefs
import com.gps.wareply.data.StyleExample
import com.gps.wareply.data.ConversationStore
import com.gps.wareply.service.IncomingMessage
import kotlinx.coroutines.launch

@Composable
fun AiTab() {
    @Suppress("UNUSED_VARIABLE")
    val v = Prefs.version.intValue
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var probe by remember { mutableIntStateOf(0) }
    val ready = remember(probe) { ModelStore.isReady(ctx) }
    val sizeMb = remember(probe) { ModelStore.sizeBytes(ctx) / 1024 / 1024 }

    var importing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var testInput by remember { mutableStateOf("Bhai kal site pe aa rahe ho kya?") }
    var testOutput by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var editingStyle by remember { mutableStateOf<StyleExample?>(null) }
    var harvested by remember { mutableStateOf<Int?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true; status = null; progress = "Starting…"
        scope.launch {
            try {
                val bytes = ModelStore.importFrom(ctx, uri) { copied, total ->
                    progress = if (total > 0)
                        "${copied / 1024 / 1024} MB of ${total / 1024 / 1024} MB"
                    else "${copied / 1024 / 1024} MB"
                }
                status = "Imported ${bytes / 1024 / 1024} MB."
            } catch (e: Throwable) {
                status = e.message ?: "Import failed"
            } finally {
                importing = false; probe++
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Model", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (ready) "Gemma loaded from storage — $sizeMb MB."
                        else "No model imported yet. Auto-replies will use your fixed rule text until one is.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    if (importing) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        Text(progress, style = MaterialTheme.typography.bodySmall)
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                picker.launch(arrayOf("*/*"))
                            }) { Text(if (ready) "Replace" else "Import .task file") }
                            if (ready) OutlinedButton(onClick = {
                                ModelStore.delete(ctx); probe++; testOutput = null
                            }) { Text("Remove") }
                        }
                    }
                    status?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("How to get the model", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "1. Sign in at huggingface.co and open litert-community/Gemma3-1B-IT.\n" +
                            "2. Accept the Gemma licence — the files stay locked until you do.\n" +
                            "3. Download gemma3-1b-it-int4.task, about 555 MB. Use Wi-Fi.\n" +
                            "4. Come back here and tap Import, then pick it from Downloads.\n\n" +
                            "The file never leaves your phone, and this app has no internet " +
                            "permission at all — which is also why it can't fetch the model for you.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Before it sends", style = MaterialTheme.typography.titleMedium)
                    AiSwitch(
                        "Show me the reply first",
                        "The bubble opens with the AI's draft. Read it, edit it if you want, " +
                            "then tap Send this. Nothing goes out until you do.",
                        Prefs.requireApproval
                    ) { Prefs.requireApproval = it }
                    if (Prefs.requireApproval) {
                        Text(
                            "Wait ${Prefs.approvalWindowSeconds}s for me, then drop it",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Slider(
                            value = Prefs.approvalWindowSeconds.toFloat(),
                            onValueChange = { Prefs.approvalWindowSeconds = it.toInt() },
                            valueRange = 15f..600f, steps = 38
                        )
                        Text(
                            "Needs the floating bubble switched on, since that is what you tap.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "Replies are sent the moment they are written, without you seeing them.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Behaviour", style = MaterialTheme.typography.titleMedium)
                    AiSwitch(
                        "Let the model write replies",
                        "Applies only to rules you've ticked \"Write with AI\" on.",
                        Prefs.aiEnabled
                    ) { Prefs.aiEnabled = it }
                    AiSwitch(
                        "Fall back to fixed text",
                        "If the model is slow, fails, or isn't imported, send the rule's text instead of nothing.",
                        Prefs.aiFallbackToCanned
                    ) { Prefs.aiFallbackToCanned = it }
                    AiSwitch(
                        "Gemma chat template",
                        "Leave on for instruction-tuned models (the -IT ones). Turn off for base models.",
                        Prefs.aiUseChatTemplate
                    ) { Prefs.aiUseChatTemplate = it }

                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = Prefs.aiPersona,
                        onValueChange = { Prefs.aiPersona = it },
                        label = { Text("Who it's writing as") },
                        placeholder = { Text("You are a civil engineer, usually at site, replying briefly.") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = Prefs.aiMarker,
                        onValueChange = { Prefs.aiMarker = it },
                        label = { Text("Appended to every AI reply") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Clearing this means people can't tell the reply wasn't written by you.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Write like me", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Show the AI a few replies you have actually written and it copies " +
                            "your tone, length and language mix. The model itself is not " +
                            "changed — the examples are sent with every request — so a handful " +
                            "of good ones beats a long list.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    AiSwitch("Use my examples", null, Prefs.styleEnabled) {
                        Prefs.styleEnabled = it
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { editingStyle = StyleExample() }) { Text("Add one") }
                        OutlinedButton(onClick = {
                            scope.launch {
                                val existing = Prefs.styleExamples()
                                val known = existing.map { it.reply.trim().lowercase() }.toHashSet()
                                val additions = ConversationStore.manualReplies(ctx, 12)
                                    .filter { it.reply.trim().lowercase() !in known }
                                    .distinctBy { it.reply.trim().lowercase() }
                                    .map { StyleExample(incoming = it.incoming, reply = it.reply) }
                                if (additions.isNotEmpty()) Prefs.saveStyleExamples(existing + additions)
                                harvested = additions.size
                            }
                        }) { Text("Learn from my replies") }
                    }
                    harvested?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (it > 0) "Added $it from replies you typed yourself."
                            else "No new ones found — reply manually from the bubble a few " +
                                "times and they will show up here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    val examples = Prefs.styleExamples()
                    if (examples.isEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "No examples yet. Replies will use the persona above only.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    examples.forEach { ex ->
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(Modifier.padding(12.dp).fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "Them: ${ex.incoming}",
                                        Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Switch(
                                        checked = ex.enabled,
                                        onCheckedChange = {
                                            Prefs.upsertStyleExample(ex.copy(enabled = it))
                                        }
                                    )
                                }
                                Text("You: ${ex.reply}", style = MaterialTheme.typography.bodyMedium)
                                Row {
                                    TextButton(onClick = { editingStyle = ex }) { Text("Edit") }
                                    TextButton(onClick = {
                                        Prefs.deleteStyleExample(ex.id)
                                    }) { Text("Delete") }
                                }
                            }
                        }
                    }
                    if (examples.size > 6) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Only the first six switched-on examples are sent.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Limits", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Reply length: ${Prefs.aiMaxWords} words",
                        style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = Prefs.aiMaxWords.toFloat(),
                        onValueChange = { Prefs.aiMaxWords = it.toInt() },
                        valueRange = 5f..80f, steps = 14
                    )
                    Text("Give up after: ${Prefs.aiTimeoutSeconds}s",
                        style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = Prefs.aiTimeoutSeconds.toFloat(),
                        onValueChange = { Prefs.aiTimeoutSeconds = it.toInt() },
                        valueRange = 5f..120f, steps = 22
                    )
                    Text(
                        if (Prefs.aiIdleUnloadMinutes == 0) "Keep model in memory always"
                        else "Free memory after ${Prefs.aiIdleUnloadMinutes} idle min",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = Prefs.aiIdleUnloadMinutes.toFloat(),
                        onValueChange = { Prefs.aiIdleUnloadMinutes = it.toInt() },
                        valueRange = 0f..60f, steps = 11
                    )
                    Text(
                        "The model holds around a gigabyte of RAM while loaded. Shorter is " +
                            "kinder to the phone; longer makes the first reply after a quiet " +
                            "spell much faster.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Try it", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Run a fake incoming message through the model. Nothing is sent.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = testInput,
                        onValueChange = { testInput = it },
                        label = { Text("Pretend someone sent") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        enabled = ready && !testing,
                        onClick = {
                            testing = true; testOutput = null
                            scope.launch {
                                val fake = IncomingMessage(
                                    chatKey = "test", sender = "Ramesh", groupName = null,
                                    text = testInput, isGroup = false,
                                    postedAt = System.currentTimeMillis(), contentIntent = null
                                )
                                val started = System.currentTimeMillis()
                                val out = ReplyWriter.write(ctx, fake)
                                val secs = (System.currentTimeMillis() - started) / 1000.0
                                testOutput = if (out != null)
                                    "$out${Prefs.aiMarker}\n\n(${"%.1f".format(secs)}s)"
                                else
                                    "No reply. " + (ReplyWriter.lastError() ?: "Timed out — try a longer limit.")
                                testing = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (testing) "Thinking…" else "Generate a reply") }

                    if (testing) {
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(
                            "First run loads the model, which takes longest.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    testOutput?.let {
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(it, Modifier.padding(12.dp).fillMaxWidth(),
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        item {
            Text(
                "A 1B model is small. It will sometimes write something bland or slightly " +
                    "off. Keep approval on, the group switch on, and the cooldown generous.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start
            )
        }
    }

    editingStyle?.let { ex ->
        StyleEditor(
            example = ex,
            onDismiss = { editingStyle = null },
            onSave = { Prefs.upsertStyleExample(it); editingStyle = null; harvested = null }
        )
    }
}

@Composable
private fun StyleEditor(
    example: StyleExample,
    onDismiss: () -> Unit,
    onSave: (StyleExample) -> Unit
) {
    var incoming by remember { mutableStateOf(example.incoming) }
    var reply by remember { mutableStateOf(example.reply) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Example reply") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = incoming, onValueChange = { incoming = it },
                    label = { Text("When someone writes") },
                    minLines = 2, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = reply, onValueChange = { reply = it },
                    label = { Text("I reply something like") },
                    minLines = 2, modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Use your real words, including the way you mix Hindi, Odia and English.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = incoming.isNotBlank() && reply.isNotBlank(),
                onClick = {
                    onSave(example.copy(incoming = incoming.trim(), reply = reply.trim()))
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AiSwitch(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
