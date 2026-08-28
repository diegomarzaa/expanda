package dev.diego.expanda.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.diego.expanda.BuildConfig
import dev.diego.expanda.R

internal object ProjectLinks {
    const val REPOSITORY = "https://github.com/diegomarzaa/expanda"
    const val ISSUES = "$REPOSITORY/issues"
    const val AUTHOR_GITHUB = "https://github.com/diegomarzaa"
    const val SUPPORT = "https://ko-fi.com/diegomarza"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AboutExpandaSheet(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                modifier = Modifier.size(96.dp),
                shape = RoundedCornerShape(26.dp),
                color = Color.White,
                shadowElevation = 5.dp,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_app_panda),
                    contentDescription = "Expanda logo",
                    modifier = Modifier.padding(11.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text("Expanda", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Type less, say more.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Version ${BuildConfig.VERSION_NAME} · GPLv3",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Local and open", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Expanda runs on your device and has no Internet permission, accounts, ads or analytics. " +
                            "Its source is public, and GPLv3 keeps distributed changes open.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "I built it quickly for my own use with extensive AI help. I am not an Android developer, " +
                            "and parts of the code still need human review. Bug reports and pull requests are welcome.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            AboutLink(
                icon = Icons.Default.Code,
                title = "Source code",
                subtitle = "github.com/diegomarzaa/expanda",
                onClick = { uriHandler.openUri(ProjectLinks.REPOSITORY) },
            )
            AboutLink(
                icon = Icons.Default.BugReport,
                title = "Issues and contributions",
                subtitle = "Report bugs or open a pull request",
                onClick = { uriHandler.openUri(ProjectLinks.ISSUES) },
            )
            AboutLink(
                icon = Icons.Default.Person,
                title = "Diego on GitHub",
                subtitle = "@diegomarzaa",
                onClick = { uriHandler.openUri(ProjectLinks.AUTHOR_GITHUB) },
            )
            AboutLink(
                icon = Icons.Default.Favorite,
                title = "Support Expanda",
                subtitle = "Buy me a coffee on Ko-fi",
                onClick = { uriHandler.openUri(ProjectLinks.SUPPORT) },
            )
            Text(
                "Inspired by Typing Hero and Expandroid, with offline Espanso compatibility in development.",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 14.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun AboutLink(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}
