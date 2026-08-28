package dev.diego.expanda.ui

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Process
import android.widget.ImageView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class LaunchableApp(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
)

internal fun loadLaunchableApps(context: Context): List<LaunchableApp> {
    val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return emptyList()
    return runCatching {
        launcherApps.getActivityList(null, Process.myUserHandle())
            .asSequence()
            .filter { it.applicationInfo.packageName != context.packageName }
            .map {
                LaunchableApp(
                    packageName = it.applicationInfo.packageName,
                    label = it.label?.toString().orEmpty().ifBlank { it.applicationInfo.packageName },
                    icon = runCatching { it.getBadgedIcon(0) }.getOrNull(),
                )
            }
            .distinctBy(LaunchableApp::packageName)
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, LaunchableApp::label))
            .toList()
    }.getOrDefault(emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppExclusionPicker(
    title: String,
    selectedPackages: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit,
) {
    val context = LocalContext.current
    val installedApps by produceState<List<LaunchableApp>>(emptyList(), context) {
        value = withContext(Dispatchers.IO) { loadLaunchableApps(context.applicationContext) }
    }
    var selected by remember(selectedPackages) { mutableStateOf(selectedPackages) }
    var query by remember { mutableStateOf("") }
    var selectedOnly by remember { mutableStateOf(false) }
    var showManual by remember { mutableStateOf(false) }
    var manualPackage by remember { mutableStateOf("") }

    val knownPackages = installedApps.mapTo(hashSetOf(), LaunchableApp::packageName)
    val unknown = selected.filterNot { it in knownPackages }.map { LaunchableApp(it, it, null) }
    val visibleApps = (installedApps + unknown)
        .distinctBy(LaunchableApp::packageName)
        .filter { app ->
            (!selectedOnly || app.packageName in selected) &&
                (query.isBlank() || app.label.contains(query, true) || app.packageName.contains(query, true))
        }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(max = 680.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(
                "${selected.size} excluded",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) },
                label = { Text("Search apps") },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = selectedOnly, onClick = { selectedOnly = !selectedOnly }, label = { Text("Selected") })
                TextButton(onClick = { selected = emptySet() }, enabled = selected.isNotEmpty()) { Text("Clear") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { showManual = !showManual }) { Text("Add package") }
            }
            if (showManual) {
                OutlinedTextField(
                    value = manualPackage,
                    onValueChange = { manualPackage = it.trim() },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Package name") },
                    supportingText = { Text("Advanced fallback, for example com.example.app") },
                    trailingIcon = {
                        TextButton(
                            onClick = {
                                manualPackage.takeIf(String::isNotBlank)?.let { selected = selected + it }
                                manualPackage = ""
                                showManual = false
                            },
                            enabled = manualPackage.isNotBlank(),
                        ) { Text("Add") }
                    },
                )
            }
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(visibleApps, key = LaunchableApp::packageName) { app ->
                    val checked = app.packageName in selected
                    ListItem(
                        modifier = Modifier.fillMaxWidth().clickable {
                            selected = if (checked) selected - app.packageName else selected + app.packageName
                        },
                        leadingContent = {
                            if (app.icon != null) {
                                AndroidView(
                                    factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.CENTER_INSIDE } },
                                    update = { it.setImageDrawable(app.icon) },
                                    modifier = Modifier.size(40.dp),
                                )
                            } else {
                                Icon(Icons.Default.Apps, null, modifier = Modifier.size(32.dp))
                            }
                        },
                        headlineContent = { Text(app.label, maxLines = 1) },
                        supportingContent = { Text(app.packageName, maxLines = 1) },
                        trailingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = { onSave(selected) }) { Text("Done") }
            }
        }
    }
}
