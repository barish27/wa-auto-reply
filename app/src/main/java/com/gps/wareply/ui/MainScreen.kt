package com.gps.wareply.ui

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gps.wareply.bubble.BubbleManager
import com.gps.wareply.data.ConversationEntry
import com.gps.wareply.data.ConversationStore
import com.gps.wareply.data.MatchType
import com.gps.wareply.data.Prefs
import com.gps.wareply.data.Rule
import com.gps.wareply.service.AutoReplyEngine
import com.gps.wareply.service.WaNotificationListener
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WA Auto Reply") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                listOf("Setup", "Rules", "AI", "History").forEachIndexed { i, label ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
                }
            }
            when (tab) {
                0 -> SetupTab(ctx)
                1 -> RulesTab()
                2 -> AiTab()
                else -> HistoryTab()
            }
        }
    }
}

/* ------------------------------------------------------------------ Setup */

@Composable
private fun SetupTab(ctx: Context) {
    @Suppress("UNUSED_VARIABLE")
    val v = Prefs.version.intValue   // subscribe to every settings change

    // Re-check permissions whenever the user returns from a system screen.
    var probe by remember { mutableIntStateOf(0) }
    val notifOk = remember(probe) { WaNotificationListener.isEnabled(ctx) }
    val overlayOk = remember(probe) { Settings.canDrawOverlays(ctx) }
    val batteryOk = remember(probe) { ignoringBatteryOptimisation(ctx) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Permissions", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "All three are needed. On Android 13+ a sideloaded app must first be " +
                            "allowed in App info → ⋮ → Allow restricted settings, or notification " +
                            "access stays greyed out.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    PermissionRow("Notification access", notifOk) {
                        ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        probe++
                    }
                    PermissionRow("Display over other apps", overlayOk) {
                        ctx.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + ctx.packageName)
                            )
                        )
                        probe++
                    }
                    PermissionRow("Ignore battery optimisation", batteryOk) {
                        ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        probe++
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { probe++ }) { Text("Re-check") }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Behaviour", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    SwitchRow(
                        "Auto-reply on my behalf",
                        "Answers with the first matching rule below.",
                        Prefs.autoReplyEnabled
                    ) { Prefs.autoReplyEnabled = it }
                    SwitchRow(
                        "Floating reply bubble",
                        "Pops up over any app so you can reply without opening WhatsApp.",
                        Prefs.bubbleEnabled
                    ) { Prefs.bubbleEnabled = it }
                    SwitchRow(
                        "Never auto-reply in groups",
                        "Strongly recommended — a canned reply in a group is visible to everyone.",
                        Prefs.skipGroups
                    ) { Prefs.skipGroups = it }
                    SwitchRow(
                        "Only while the screen is off",
                        "Won't auto-reply when you're actually using the phone.",
                        Prefs.onlyWhenScreenOff
                    ) { Prefs.onlyWhenScreenOff = it }
                    SwitchRow(
                        "Vibrate when a bubble appears", null, Prefs.vibrateOnBubble
                    ) { Prefs.vibrateOnBubble = it }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Cooldown", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Wait ${Prefs.cooldownMinutes} min before auto-replying to the same chat again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = Prefs.cooldownMinutes.toFloat(),
                        onValueChange = { Prefs.cooldownMinutes = it.toInt() },
                        valueRange = 0f..240f,
                        steps = 23
                    )
                    TextButton(onClick = { AutoReplyEngine.resetCooldowns() }) {
                        Text("Clear cooldowns now")
                    }
                    Text(
                        "Tap this while testing — otherwise the second message you send " +
                            "yourself is ignored until the window passes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Active hours", style = MaterialTheme.typography.titleMedium)
                    SwitchRow(
                        "Only auto-reply between set hours", null, Prefs.useActiveHours
                    ) { Prefs.useActiveHours = it }
                    if (Prefs.useActiveHours) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { pickTime(ctx, Prefs.activeFrom) { Prefs.activeFrom = it } },
                                modifier = Modifier.weight(1f)
                            ) { Text("From ${fmtMinutes(Prefs.activeFrom)}") }
                            OutlinedButton(
                                onClick = { pickTime(ctx, Prefs.activeTo) { Prefs.activeTo = it } },
                                modifier = Modifier.weight(1f)
                            ) { Text("To ${fmtMinutes(Prefs.activeTo)}") }
                        }
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = {
                    if (Settings.canDrawOverlays(ctx)) BubbleManager.showDemo(ctx)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Show a preview bubble") }
        }

        item {
            Text(
                "Replies are sent through WhatsApp's own notification reply channel — the same " +
                    "one Android Auto uses. Nothing is read from WhatsApp's storage and no " +
                    "message leaves your phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (granted) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = if (granted) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.width(12.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onClick) { Text(if (granted) "Open" else "Grant") }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
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

/* ------------------------------------------------------------------ Rules */

@Composable
private fun RulesTab() {
    @Suppress("UNUSED_VARIABLE")
    val v = Prefs.version.intValue
    var editing by remember { mutableStateOf<Rule?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 88.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "The first enabled rule that matches an incoming message wins. " +
                        "Use {name}, {fullname}, {chat} and {time} inside a reply.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(Prefs.rules(), key = { it.id }) { rule ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(rule.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    when (rule.match) {
                                        MatchType.ANY_MESSAGE -> "Any message"
                                        MatchType.CONTACT_CONTAINS -> "Sender contains \"${rule.value}\""
                                        MatchType.TEXT_CONTAINS -> "Message contains \"${rule.value}\""
                                    } + (if (rule.includeGroups) " · groups too" else "")
                                        + (if (rule.useAi) " · AI" else ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = rule.enabled,
                                onCheckedChange = { Prefs.upsertRule(rule.copy(enabled = it)) }
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(rule.reply, style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { editing = rule }) { Text("Edit") }
                            TextButton(onClick = { Prefs.deleteRule(rule.id) }) { Text("Delete") }
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { editing = Rule(reply = "") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
        ) { Icon(Icons.Filled.Add, contentDescription = "Add rule") }
    }

    editing?.let { rule ->
        RuleEditor(
            rule = rule,
            onDismiss = { editing = null },
            onSave = { Prefs.upsertRule(it); editing = null }
        )
    }
}

@Composable
private fun RuleEditor(rule: Rule, onDismiss: () -> Unit, onSave: (Rule) -> Unit) {
    var name by remember { mutableStateOf(rule.name) }
    var match by remember { mutableStateOf(rule.match) }
    var value by remember { mutableStateOf(rule.value) }
    var reply by remember { mutableStateOf(rule.reply) }
    var groups by remember { mutableStateOf(rule.includeGroups) }
    var useAi by remember { mutableStateOf(rule.useAi) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reply rule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Trigger", style = MaterialTheme.typography.labelMedium)
                MatchType.entries.forEach { t ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = match == t, onClick = { match = t })
                        Text(t.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (match != MatchType.ANY_MESSAGE) {
                    OutlinedTextField(
                        value = value, onValueChange = { value = it },
                        label = { Text("Text to look for") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = useAi, onCheckedChange = { useAi = it })
                    Text("Write with AI", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = reply, onValueChange = { reply = it },
                    label = { Text(if (useAi) "Fallback text" else "Reply to send") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                if (useAi) Text(
                    "Sent when the model is slow or unavailable.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = groups, onCheckedChange = { groups = it })
                    Text("Apply in group chats too", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = reply.isNotBlank() || useAi,
                onClick = {
                    onSave(
                        rule.copy(
                            name = name.ifBlank { "Rule" },
                            match = match,
                            value = value.trim(),
                            reply = reply.trim(),
                            includeGroups = groups,
                            useAi = useAi
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/* ---------------------------------------------------------------- History */

@Composable
private fun HistoryTab() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val entries by remember(query) { ConversationStore.history(ctx, query) }
        .collectAsState(initial = emptyList())

    if (entries.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedTextField(query, { query = it }, label = { Text("Search history") })
                Spacer(Modifier.height(12.dp))
                Text("No matching local history.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                label = { Text("Search conversations") }, modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            TextButton(onClick = { scope.launch { ConversationStore.deleteAll(ctx) } }) {
                Text("Delete all stored history")
            }
        }
        items(entries, key = { it.id }) { e -> LogCard(e) {
            scope.launch { ConversationStore.deleteContact(ctx, e.contactKey) }
        } }
    }
}

@Composable
private fun LogCard(e: ConversationEntry, onDeleteContact: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(e.contactName, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            when {
                                e.skippedReason != null -> "skipped"
                                e.aiWritten -> "AI"
                                e.automatic -> "auto"
                                else -> "manual"
                            }
                        )
                    }
                )
            }
            Text(
                SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(e.createdAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text("“${e.incoming}”", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            if (e.skippedReason != null) {
                Text(
                    "Did not reply — ${e.skippedReason}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }
            Text(
                "→ ${e.reply}",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                color = if (e.delivered) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )
            if (!e.delivered) Text(
                "failed to send",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            TextButton(onClick = onDeleteContact) { Text("Delete this contact's history") }
        }
    }
}

/* ------------------------------------------------------------------ utils */

private fun fmtMinutes(m: Int): String {
    val h = m / 60
    val mm = m % 60
    val ampm = if (h < 12) "AM" else "PM"
    val h12 = when {
        h % 12 == 0 -> 12
        else -> h % 12
    }
    return String.format(Locale.getDefault(), "%d:%02d %s", h12, mm, ampm)
}

private fun pickTime(ctx: Context, current: Int, onPicked: (Int) -> Unit) {
    TimePickerDialog(
        ctx,
        { _, h, m -> onPicked(h * 60 + m) },
        current / 60, current % 60, false
    ).show()
}

private fun ignoringBatteryOptimisation(ctx: Context): Boolean {
    val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
    return pm.isIgnoringBatteryOptimizations(ctx.packageName)
}
