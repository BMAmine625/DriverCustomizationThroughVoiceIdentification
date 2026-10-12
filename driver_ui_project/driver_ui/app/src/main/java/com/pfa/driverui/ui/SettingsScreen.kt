package com.pfa.driverui.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pfa.driverui.model.DEFAULT_PREFERENCES
import com.pfa.driverui.model.DriverPreferences
import com.pfa.driverui.network.ApiClient
import com.pfa.driverui.network.EnrollEvent
import com.pfa.driverui.network.ServerConfig
import com.pfa.driverui.ui.theme.CarAmber
import com.pfa.driverui.ui.theme.CarOnDark
import com.pfa.driverui.ui.theme.CarOutline
import com.pfa.driverui.ui.theme.CarSurface
import com.pfa.driverui.ui.theme.CarSurfaceVariant
import com.pfa.driverui.ui.theme.CarTeal

/**
 * State of the settings mode: the profile list, the selected profile, and the edited copy ([working]) which also
 * drives the 3D car live. [saved] is what the server has. [guard] asks "save changes?" before a leaving action
 * when [working] differs from [saved].
 */
class SettingsState(initialHost: String) {
    /** Server address (host:port) used by the settings; [changeHost] switches it and re-checks the connection. */
    var host by mutableStateOf(initialHost)
        private set

    var client: ApiClient = ApiClient(initialHost)
        private set

    var drivers by mutableStateOf<List<String>>(emptyList())
    var selected by mutableStateOf<String?>(null)
    var saved by mutableStateOf(DEFAULT_PREFERENCES)
    var working by mutableStateOf(DEFAULT_PREFERENCES)
    var message by mutableStateOf<String?>(null)
    var pendingLeave by mutableStateOf<(() -> Unit)?>(null)

    /** Options of the selected profile, kept on the server: automatic profile improvement + number of saved samples. */
    var adapt by mutableStateOf(false)
        private set
    var sampleCount by mutableStateOf<Int?>(null)
        private set
    var reenrolling by mutableStateOf(false)
        private set

    /** Last known saved preferences per profile (used to restore the identified driver after leaving settings). */
    val cache = mutableMapOf<String, DriverPreferences>()

    val dirty: Boolean get() = selected != null && working != saved

    /** Called when the settings mode opens. [driverName]/[prefs] = driver identified before, if any. */
    fun open(driverName: String?, prefs: DriverPreferences?) {
        selected = driverName
        val p = prefs ?: DEFAULT_PREFERENCES
        saved = p
        working = p
        if (driverName != null) cache[driverName] = p
        message = null
        pendingLeave = null
        refresh()
        if (driverName != null) loadOptions(driverName)
    }

    /** Loads the voice options (automatic improvement, saved samples) of [name]. */
    fun loadOptions(name: String) {
        adapt = false
        sampleCount = null
        client.fetchOptions(
            driverName = name,
            onResult = { a, n ->
                if (selected == name) { adapt = a; sampleCount = n }
            },
            onError = { if (selected == name) message = "Could not load voice options: $it" },
        )
    }

    /** Switch "Improve profile automatically": applied on the server at once, no need to press Save. */
    fun changeAdapt(value: Boolean) {
        val name = selected ?: return
        val previous = adapt
        adapt = value
        client.setAdapt(
            driverName = name,
            adapt = value,
            onSuccess = {
                message = if (value) "$name's profile will now improve automatically" else "Automatic improvement turned off for $name"
            },
            onError = {
                if (selected == name) adapt = previous
                message = "Could not change the option: $it"
            },
        )
    }

    /** Rebuilds the voice profile from the recordings already saved (no new recording). */
    fun reenrollFromSaved() {
        val name = selected ?: return
        if (reenrolling) return
        reenrolling = true
        message = "Re-enrolling $name from the saved recordings…"
        client.reenroll(
            driverName = name,
            onSuccess = { n ->
                reenrolling = false
                message = "Voice profile of $name rebuilt from $n saved recordings"
                loadOptions(name)
            },
            onError = {
                reenrolling = false
                message = "Re-enroll failed: $it"
            },
        )
    }

    /** After "Record again" finished: the old recordings were replaced by the new ones. */
    fun recordedAgain(name: String, samples: Int) {
        message = "Voice profile of $name replaced with $samples new recordings"
        if (selected == name) loadOptions(name)
    }

    /** Uses [newHost] from now on and tells the driver whether the server answers. */
    fun changeHost(newHost: String) {
        host = newHost
        client = ApiClient(newHost)
        message = "Server set to $newHost, checking…"
        client.fetchDrivers(
            onResult = {
                drivers = it
                message = "Server saved: $newHost (connected, ${it.size} profiles)"
            },
            onError = { message = "Server saved: $newHost, but it does not answer ($it)" },
        )
    }

    fun refresh() {
        client.fetchDrivers(
            onResult = { drivers = it },
            onError = { message = "Could not load profiles: $it" },
        )
    }

    fun select(name: String) {
        client.fetchPreferences(
            driverName = name,
            onResult = { p ->
                val prefs = p ?: DEFAULT_PREFERENCES
                cache[name] = prefs
                selected = name
                saved = prefs
                working = prefs
                message = null
                loadOptions(name)
            },
            onError = { message = "Could not load preferences: $it" },
        )
    }

    fun save(then: () -> Unit = {}) {
        val name = selected ?: return
        val toSave = working
        client.savePreferences(
            driverName = name,
            preferences = toSave,
            onSuccess = {
                saved = toSave
                cache[name] = toSave
                message = "Saved"
                then()
            },
            onError = { message = "Save failed: $it" },
        )
    }

    fun delete(name: String) {
        client.deleteDriver(
            driverName = name,
            onSuccess = {
                cache.remove(name)
                if (selected == name) {
                    selected = null
                    saved = DEFAULT_PREFERENCES
                    working = DEFAULT_PREFERENCES
                }
                message = "Deleted $name"
                refresh()
            },
            onError = { message = "Delete failed: $it" },
        )
    }

    fun rename(old: String, new: String) {
        client.renameDriver(
            oldName = old,
            newName = new,
            onSuccess = {
                cache.remove(old)?.let { cache[new] = it }
                if (selected == old) { selected = new; loadOptions(new) }
                message = "Renamed to $new"
                refresh()
            },
            onError = { message = "Rename failed: $it" },
        )
    }

    /** After a voice enrollment: store default preferences for the new profile, then select it. */
    fun createdProfile(name: String) {
        client.savePreferences(
            driverName = name,
            preferences = DEFAULT_PREFERENCES,
            onSuccess = {
                refresh()
                select(name)
            },
            onError = {
                message = "Profile created, but saving defaults failed: $it"
                refresh()
            },
        )
    }

    /** Runs [action] now, or after the driver answered the "save changes?" popup if there are unsaved changes. */
    fun guard(action: () -> Unit) {
        if (dirty) pendingLeave = action else action()
    }

    fun confirmSave() {
        val action = pendingLeave
        pendingLeave = null
        save { action?.invoke() }
    }

    fun confirmDiscard() {
        val action = pendingLeave
        pendingLeave = null
        working = saved
        action?.invoke()
    }

    fun cancelLeave() {
        pendingLeave = null
    }
}

/** The "save changes?" popup, shown whenever [SettingsState.guard] found unsaved changes. */
@Composable
fun UnsavedChangesDialog(state: SettingsState) {
    if (state.pendingLeave == null) return
    AlertDialog(
        onDismissRequest = { state.cancelLeave() },
        title = { Text("Save changes?") },
        text = { Text("You changed ${state.selected ?: "this profile"}'s preferences. Save them before leaving?") },
        confirmButton = {
            TextButton(onClick = { state.confirmSave() }) { Text("Save", color = CarTeal) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { state.confirmDiscard() }) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = { state.cancelLeave() }) { Text("Cancel") }
            }
        },
    )
}

/**
 * Left menu of the settings mode: profile list, save / rename / delete for the selected profile, add profile
 * (with voice enrollment) and the server address.
 */
@Composable
fun ProfilePanel(
    state: SettingsState,
    onHostSaved: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val host = state.host
    var adding by remember { mutableStateOf(false) }
    var recordingAgain by remember { mutableStateOf<String?>(null) }
    var reenrollTarget by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var showServer by remember { mutableStateOf(false) }
    var hostText by remember(host) { mutableStateOf(host) }

    Surface(modifier = modifier, color = CarSurface) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Profiles",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                    color = CarOnDark,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close settings", tint = CarOnDark)
                }
            }
            Spacer(Modifier.height(8.dp))

            val again = recordingAgain
            if (again != null) {
                EnrollSection(
                    state = state,
                    fixedName = again,
                    onFinished = { recordingAgain = null },
                    onCancel = { recordingAgain = null },
                    modifier = Modifier.weight(1f),
                )
            } else if (adding) {
                EnrollSection(
                    state = state,
                    onFinished = { adding = false },
                    onCancel = { adding = false },
                    modifier = Modifier.weight(1f),
                )
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.drivers.isEmpty()) {
                        Text("No profiles yet.", color = CarOnDark.copy(alpha = 0.6f), fontSize = 14.sp)
                    }
                    state.drivers.forEach { name ->
                        ProfileRow(
                            name = name,
                            selected = name == state.selected,
                            unsaved = name == state.selected && state.dirty,
                        ) {
                            if (name != state.selected) state.guard { state.select(name) }
                        }
                    }
                }

                val current = state.selected
                if (current != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { state.save() },
                            enabled = state.dirty,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CarTeal,
                                contentColor = Color(0xFF00201B),
                            ),
                        ) { Text("Save", maxLines = 1) }
                        OutlinedButton(
                            onClick = { renameTarget = current },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                        ) { Text("Rename", maxLines = 1) }
                        OutlinedButton(
                            onClick = { deleteTarget = current },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) { Text("Delete", maxLines = 1) }
                    }
                    VoiceOptionsCard(
                        state = state,
                        onReenroll = { reenrollTarget = current },
                        onRecordAgain = { state.guard { recordingAgain = current } },
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }

                OutlinedButton(
                    onClick = { state.guard { adding = true } },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add profile")
                }
            }

            state.message?.let {
                Text(it, color = CarTeal, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
            }

            TextButton(onClick = { showServer = !showServer }, modifier = Modifier.padding(top = 4.dp)) {
                Text("Server: $host", color = CarOnDark.copy(alpha = 0.6f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (showServer) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = hostText,
                        onValueChange = { hostText = it },
                        singleLine = true,
                        label = { Text("host:port") },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        val trimmed = hostText.trim()
                        if (trimmed.isNotBlank()) {
                            ServerConfig.setHost(context, trimmed)   // persisted for the next launch
                            state.changeHost(trimmed)                // used by the settings right now + connection check
                            onHostSaved(trimmed)                     // used by the voice identification
                            focusManager.clearFocus()
                            showServer = false                       // hide the field and its Save button
                        }
                    }) { Text("Save") }
                }
            }
        }
    }

    renameTarget?.let { old ->
        var text by remember(old) { mutableStateOf(old) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename $old") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("New name") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = text.trim()
                    renameTarget = null
                    if (trimmed.isNotBlank() && trimmed != old) state.rename(old, trimmed)
                }) { Text("Rename", color = CarTeal) }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } },
        )
    }

    reenrollTarget?.let { name ->
        AlertDialog(
            onDismissRequest = { reenrollTarget = null },
            title = { Text("Re-enroll $name?") },
            text = {
                Text(
                    "The voice profile is rebuilt from the ${state.sampleCount ?: 0} recordings already saved. " +
                        "Nothing is recorded, and any automatic improvement made so far is discarded.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    reenrollTarget = null
                    state.reenrollFromSaved()
                }) { Text("Re-enroll", color = CarTeal) }
            },
            dismissButton = { TextButton(onClick = { reenrollTarget = null }) { Text("Cancel") } },
        )
    }

    deleteTarget?.let { name ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete $name?") },
            text = { Text("This removes their voice profile, preferences and recorded samples. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    state.delete(name)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }
}

/** Voice options of the selected profile: automatic improvement switch, re-enroll, record again. */
@Composable
private fun VoiceOptionsCard(
    state: SettingsState,
    onReenroll: () -> Unit,
    onRecordAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val samples = state.sampleCount
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = CarSurfaceVariant,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Voice profile", color = CarTeal, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Improve automatically", color = CarOnDark, fontSize = 14.sp)
                    Text(
                        "Learns from very confident identifications",
                        color = CarOnDark.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                    )
                }
                Switch(
                    checked = state.adapt,
                    onCheckedChange = { state.changeAdapt(it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = CarTeal, checkedThumbColor = Color(0xFF00201B)),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onReenroll,
                    enabled = (samples ?: 0) > 0 && !state.reenrolling,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                ) { Text(if (state.reenrolling) "Working…" else "Re-enroll", maxLines = 1) }
                OutlinedButton(
                    onClick = onRecordAgain,
                    enabled = !state.reenrolling,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                ) { Text("Record again", maxLines = 1) }
            }
            Text(
                text = when {
                    samples == null -> "Re-enroll: uses the saved recordings. Record again: replaces them with new ones."
                    samples == 0 -> "No saved recordings: use Record again."
                    else -> "$samples saved recordings. Re-enroll uses them; Record again replaces them with new ones."
                },
                color = CarOnDark.copy(alpha = 0.6f),
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun ProfileRow(name: String, selected: Boolean, unsaved: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = if (selected) CarTeal.copy(alpha = 0.14f) else CarSurfaceVariant,
        shape = RoundedCornerShape(16.dp),
        border = if (selected) BorderStroke(1.dp, CarTeal) else null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (selected) CarTeal else CarOutline),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = name.take(1).uppercase(),
                    color = if (selected) Color(0xFF00201B) else CarOnDark,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = name,
                color = CarOnDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (unsaved) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(CarAmber))
            }
        }
    }
}

/** Add-profile flow: name -> voice enrollment through /ws/enroll (server microphone) -> profile created. */
@Composable
private fun EnrollSection(
    state: SettingsState,
    onFinished: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    fixedName: String? = null,   // set = "Record again" for this existing profile (old recordings are replaced)
) {
    val samplesTarget = 5
    val replacing = fixedName != null
    var name by remember { mutableStateOf(fixedName ?: "") }
    var started by remember { mutableStateOf(false) }
    var recorded by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }

    DisposableEffect(started) {
        if (!started) {
            onDispose { }
        } else {
            val socket = state.client.connectEnrollWebSocket(
                driverName = name,
                samples = samplesTarget,
                replace = replacing,
                onEvent = { event ->
                    when (event) {
                        is EnrollEvent.SampleRecorded -> recorded = event.index
                        is EnrollEvent.Done -> {
                            done = true
                            if (replacing) state.recordedAgain(name, event.samplesTotal) else state.createdProfile(name)
                            onFinished()
                        }
                        is EnrollEvent.Error -> error = event.message
                    }
                },
                onFailure = { message -> error = message },
            )
            onDispose { if (!done) socket.close(1000, "leaving enrollment") }
        }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            if (replacing) "Record $name again" else "New profile",
            fontSize = 18.sp, fontWeight = FontWeight.Medium, color = CarTeal,
        )
        if (!started) {
            if (replacing) {
                Text(
                    text = "You will record $samplesTarget new samples. $name's saved recordings are replaced only when all " +
                        "of them are recorded; if you cancel before that, nothing changes.",
                    color = CarOnDark.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                )
            } else {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Driver name") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val clash = !replacing && name.trim() in state.drivers
            if (clash) Text("This name already exists.", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = {
                        name = name.trim()
                        started = true
                    },
                    enabled = name.isNotBlank() && !clash && '/' !in name,
                    modifier = Modifier.weight(1.6f),
                    colors = ButtonDefaults.buttonColors(containerColor = CarTeal, contentColor = Color(0xFF00201B)),
                ) { Text(if (replacing) "Start recording" else "Start voice enrollment", maxLines = 1) }
            }
        } else {
            Text(
                text = "Say a short phrase, pause, and repeat until all samples are recorded. The microphone of the server is used.",
                color = CarOnDark.copy(alpha = 0.8f),
                fontSize = 14.sp,
            )
            Text("$recorded / $samplesTarget samples", color = CarOnDark.copy(alpha = 0.6f), fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 1..samplesTarget) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (i <= recorded) CarTeal else CarOutline),
                    )
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    }
}
