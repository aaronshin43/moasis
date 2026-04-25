package com.example.moasis.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import com.example.moasis.ui.component.OasisWordmark

@Composable
fun HomeScreen(
    isAiEnabled: Boolean,
    aiStatusText: String?,
    aiProgress: Float?,
    isAiPreparing: Boolean,
    isAiReady: Boolean,
    canRetryAiPreparation: Boolean,
    aiModelLabel: String?,
    aiRouteText: String?,
    aiCacheSummaryText: String?,
    onStart: (String) -> Unit,
    onRetryAiPreparation: () -> Unit,
    onVoiceInput: () -> Unit,
    transcriptDraft: String,
    isListening: Boolean,
) {
    var draft by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OasisWordmark(
            textStyle = MaterialTheme.typography.headlineSmall,
            textColor = MaterialTheme.colorScheme.onBackground,
            dotSize = 8.dp,
            spacing = 3.dp,
            dotYOffset = 2.dp,
        )
        Text(
            text = "Describe the emergency in one short sentence.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = when {
                !isAiEnabled -> "AI disabled: deterministic guidance only"
                isAiReady -> "AI personalization enabled: model ready"
                isAiPreparing -> "AI personalization enabled: preparing model"
                else -> "AI personalization enabled: model not ready yet"
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        aiStatusText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        aiModelLabel?.let {
            Text(
                text = "Model: $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        aiRouteText?.let {
            Text(
                text = "Route: $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        aiCacheSummaryText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isAiEnabled && (isAiPreparing || aiProgress != null)) {
            LinearProgressIndicator(
                progress = { aiProgress ?: 0f },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (isAiEnabled && canRetryAiPreparation && !isAiPreparing && !isAiReady) {
            OutlinedButton(
                onClick = onRetryAiPreparation,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Retry AI model prep")
            }
        }
        if (transcriptDraft.isNotBlank()) {
            Text(
                text = transcriptDraft,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
            onClick = onVoiceInput,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Outlined.Mic,
                contentDescription = "Voice input",
            )
            Text(if (isListening) " Listening..." else " Start voice input")
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
