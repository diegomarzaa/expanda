package dev.diego.expanda.ui.tutorial

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private enum class StorageChoice {
    InExpanda,
    LocalFolder,
}

@Composable
fun WorkspaceOnboardingScreen(
    folderLinked: Boolean,
    sourceFileCount: Int,
    matchCount: Int,
    onChooseFolder: () -> Unit,
    onDone: () -> Unit,
) {
    var storageChoice by remember { mutableStateOf(StorageChoice.InExpanda) }

    LaunchedEffect(folderLinked) {
        if (folderLinked) storageChoice = StorageChoice.LocalFolder
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                if (folderLinked) "Folder linked" else "Where should snippets live?",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (folderLinked) {
                    "$sourceFileCount .yml files · $matchCount Android matches"
                } else {
                    "Both options use plain-text .yml files in Espanso format. Pick what fits you best."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))

            if (folderLinked) {
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                        Column {
                            Text("Local folder linked", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Your .yml files live in the folder you chose.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text("Continue")
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onChooseFolder, modifier = Modifier.fillMaxWidth()) {
                    Text("Choose a different folder")
                }
            } else {
                SelectableStorageOption(
                    selected = storageChoice == StorageChoice.InExpanda,
                    onClick = { storageChoice = StorageChoice.InExpanda },
                    icon = Icons.Default.PhoneAndroid,
                    title = "In Expanda",
                    detail = "Simplest option. Snippets stay inside the app. You can link a folder later from Espanso source.",
                )
                Spacer(Modifier.height(10.dp))
                SelectableStorageOption(
                    selected = storageChoice == StorageChoice.LocalFolder,
                    onClick = { storageChoice = StorageChoice.LocalFolder },
                    icon = Icons.Default.Folder,
                    title = "Local folder",
                    detail = "Same files on disk. Sync with Espanso desktop or edit with other apps.",
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        when (storageChoice) {
                            StorageChoice.InExpanda -> onDone()
                            StorageChoice.LocalFolder -> onChooseFolder()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(
                        if (storageChoice == StorageChoice.LocalFolder) "Choose folder" else "Continue",
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectableStorageOption(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    title: String,
    detail: String,
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
