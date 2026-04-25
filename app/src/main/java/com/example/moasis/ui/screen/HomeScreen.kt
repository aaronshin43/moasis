package com.example.moasis.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    isAiEnabled: Boolean,
    onStart: (String) -> Unit,
) {
    var draft by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "MOASIS",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Describe the emergency in one short sentence.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (isAiEnabled) "AI personalization enabled" else "AI disabled: deterministic guidance only",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 4,
            label = { Text("Emergency report") },
        )
        Button(
            onClick = { onStart(draft) },
            modifier = Modifier.fillMaxWidth(),
            enabled = draft.isNotBlank(),
        ) {
            Text("Start")
        }
        OutlinedButton(
            onClick = {
                draft = "I burned my arm"
                onStart(draft)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Try burn scenario")
        }
        OutlinedButton(
            onClick = {
                draft = "my friend collapsed"
                onStart(draft)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Try collapse scenario")
        }
    }
}
