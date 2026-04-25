package com.example.moasis.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import com.example.moasis.presentation.UiAction
import com.example.moasis.presentation.UiState
import com.example.moasis.ui.component.AttachedImageStrip
import com.example.moasis.ui.component.StepCard
import com.example.moasis.ui.component.VisualAidStrip
import com.example.moasis.ui.component.VoiceStatusBar
import com.example.moasis.ui.component.WarningBanner

@Composable
fun ActiveProtocolScreen(
    uiState: UiState,
    statusText: String?,
    aiGenerationStatusText: String?,
    aiStatusText: String?,
    aiProgress: Float?,
    aiDownloadedBytes: Long?,
    isAiPreparing: Boolean,
    canRetryAiPreparation: Boolean,
    aiModelLabel: String?,
    aiRouteText: String?,
    aiCacheSummaryText: String?,
    quickResponses: List<String>,
    onSubmitText: (String) -> Unit,
    onAction: (UiAction) -> Unit,
    onQuickResponse: (String) -> Unit,
    onVoiceInput: () -> Unit,
    transcriptDraft: String,
    attachedImagePaths: List<String>,
    onPickImage: () -> Unit,
    onCaptureImage: () -> Unit,
    onClearImages: () -> Unit,
    ) {
    var draft by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        statusText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        aiGenerationStatusText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
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
        aiDownloadedBytes?.takeIf { it > 0 }?.let {
            Text(
                text = "Downloaded: ${it.toReadableByteCount()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (aiProgress != null) {
            androidx.compose.material3.LinearProgressIndicator(
                progress = { aiProgress },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        VoiceStatusBar(
            isListening = uiState.isListening,
            isSpeaking = uiState.isSpeaking,
            transcriptDraft = transcriptDraft,
        )

        StepCard(
            title = uiState.title,
            instruction = uiState.primaryInstruction,
            secondaryInstruction = uiState.secondaryInstruction,
            progressText = if (uiState.totalSteps > 0) {
                "Step ${uiState.currentStep} of ${uiState.totalSteps}"
            } else {
                null
            },
            guidanceOriginLabel = uiState.guidanceOriginLabel,
        )

        uiState.warningText?.let {
            WarningBanner(warningText = it)
        }

        VisualAidStrip(visualAids = uiState.visualAids)

        AttachedImageStrip(
            imagePaths = attachedImagePaths,
            onClearImages = onClearImages,
            onRemoveImage = {},
        )

        if (quickResponses.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                quickResponses.forEach { response ->
                    OutlinedButton(
                        onClick = { onQuickResponse(response.lowercase()) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(response)
                    }
                }
            }
        }

        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4,
            label = { Text("Update or answer") },
        )

        Button(
            onClick = {
                onSubmitText(draft)
                draft = ""
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = draft.isNotBlank(),
        ) {
            Text("Submit")
        }

        if (canRetryAiPreparation && !isAiPreparing) {
            OutlinedButton(
                onClick = { onAction(UiAction.RetryAiPreparation) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Retry AI model prep")
            }
        }

        OutlinedButton(
            onClick = onVoiceInput,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Outlined.Mic,
                contentDescription = "Voice input",
            )
            Text(if (uiState.isListening) " Listening..." else " Start voice input")
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onPickImage,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Outlined.PhotoLibrary,
                    contentDescription = "Pick image",
                )
                Text(" Gallery")
            }
            OutlinedButton(
                onClick = onCaptureImage,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Outlined.CameraAlt,
                    contentDescription = "Capture image",
                )
                Text(" Camera")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = { onAction(UiAction.Repeat) },
                modifier = Modifier.weight(1f),
            ) {
                Text("Repeat")
            }
            OutlinedButton(
                onClick = { onAction(UiAction.Next) },
                modifier = Modifier.weight(1f),
            ) {
                Text("Next")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = { onAction(UiAction.Back) },
                modifier = Modifier.weight(1f),
            ) {
                Text("Back")
            }
            if (uiState.showCallEmergencyButton) {
                Button(
                    onClick = { onAction(UiAction.CallEmergency) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Emergency Call")
                }
            }
        }
    }
}

private fun Long.toReadableByteCount(): String {
    val kb = 1024L
    val mb = kb * 1024L
    val gb = mb * 1024L
    return when {
        this >= gb -> String.format("%.2f GB", this.toDouble() / gb)
        this >= mb -> String.format("%.1f MB", this.toDouble() / mb)
        this >= kb -> String.format("%.1f KB", this.toDouble() / kb)
        else -> "$this B"
    }
}
