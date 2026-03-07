package dev.trooped.tvquickbars.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.trooped.tvquickbars.R

class DialogActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dialogType = intent.getStringExtra("DIALOG_TYPE") ?: "UNKNOWN"

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = colorResource(id = R.color.md_theme_primary),
                    onPrimary = colorResource(id = R.color.md_theme_onPrimary),
                    primaryContainer = colorResource(id = R.color.md_theme_primaryContainer),
                    onPrimaryContainer = colorResource(id = R.color.md_theme_onPrimaryContainer),
                    surface = colorResource(id = R.color.md_theme_surface),
                    onSurface = colorResource(id = R.color.md_theme_onSurface),
                    surfaceVariant = colorResource(id = R.color.md_theme_surfaceVariant),
                    onSurfaceVariant = colorResource(id = R.color.md_theme_onSurfaceVariant),
                    outline = colorResource(id = R.color.md_theme_outline),
                )
            ) {
                when (dialogType) {
                    "INTEGRATION_INSTRUCTIONS" -> IntegrationInstructionsDialog { finish() }
                    else -> ErrorDialog { finish() }
                }
            }
        }
    }
}

@Composable
private fun IntegrationInstructionsDialog(onDismiss: () -> Unit) {
    var showDialog by remember { mutableStateOf(true) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = {
                showDialog = false
                onDismiss()
            },
            title = {
                Text(
                    "Import Entities via Integration",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "To import entities using the integration:",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StepTextSimple(number = 1, text = "Go to the QuickBars integration in Home Assistant")
                        StepTextSimple(number = 2, text = "Click on the \"Configure\" button")
                        StepTextSimple(number = 3, text = "Select \"Add / Remove Saved Entities\"")
                        StepTextSimple(number = 4, text = "Choose the entities you'd like to import to the app")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Note: You should exit the app and re-open after configuring entities using the integration.\n\nYou can always import entities using the TV interface by going to Entities → Import Entities",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDialog = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Got It")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun StepTextSimple(number: Int, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "$number.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )

        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorDialog(onDismiss: () -> Unit) {
    var showDialog by remember { mutableStateOf(true) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = {
                showDialog = false
                onDismiss()
            },
            title = { Text("Error") },
            text = { Text("Something went wrong.") },
            confirmButton = {
                Button(onClick = {
                    showDialog = false
                    onDismiss()
                }) {
                    Text("OK")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}