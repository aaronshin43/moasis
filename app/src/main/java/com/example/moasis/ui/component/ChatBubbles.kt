package com.example.moasis.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.moasis.presentation.ChatMessage
import com.example.moasis.presentation.UiAction
import com.example.moasis.presentation.UiState
import kotlinx.coroutines.delay

@Composable
fun UserBubble(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
fun AssistantHistoryBubble(message: ChatMessage.Assistant, modifier: Modifier = Modifier) {
    val contentPadding = if (message.title.isBlank() && message.currentStep == 0) {
        12.dp
    } else {
        16.dp
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        message.warningText?.let {
            WarningBanner(warningText = it)
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
        ) {
            Column(
                modifier = Modifier.padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = contentPadding,
                    bottom = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (message.currentStep > 0 && message.totalSteps > 0) {
                    Text(
                        text = "Step ${message.currentStep} of ${message.totalSteps}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.4.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (message.title.isNotBlank()) {
                    Text(
                        text = message.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = message.primaryInstruction,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                message.secondaryInstruction?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (message.visualAids.isNotEmpty()) {
            VisualAidStrip(visualAids = message.visualAids)
        }
    }
}

@Composable
fun CurrentStepBlock(
    uiState: UiState,
    quickReplies: List<String>,
    isInputEnabled: Boolean,
    onQuickReply: (String) -> Unit,
    onAction: (UiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentPadding = if (uiState.title.isBlank() && uiState.currentStep == 0) {
        12.dp
    } else {
        16.dp
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        uiState.warningText?.let {
            WarningBanner(warningText = it)
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
        ) {
            Column(
                modifier = Modifier.padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = contentPadding,
                    bottom = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (!uiState.isAiAnswerPending && uiState.currentStep > 0 && uiState.totalSteps > 0) {
                    Text(
                        text = "Step ${uiState.currentStep} of ${uiState.totalSteps}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.4.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (uiState.title.isNotBlank()) {
                    Text(
                        text = uiState.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (uiState.isAiAnswerPending) {
                    AnswerLoadingIndicator()
                } else {
                    Text(
                        text = uiState.primaryInstruction,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    uiState.secondaryInstruction?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (uiState.visualAids.isNotEmpty()) {
            VisualAidStrip(visualAids = uiState.visualAids)
        }

        // Quick reply chips + contextual navigation
        val allReplies = buildList {
            addAll(quickReplies)
            if (uiState.totalSteps > 0 && quickReplies.isEmpty()) {
                add("Next step")
                add("Repeat")
            }
        }
        if (allReplies.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                allReplies.forEach { reply ->
                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            when (reply) {
                                "Next step" -> onAction(UiAction.Next)
                                "Repeat" -> onAction(UiAction.Repeat)
                                else -> onQuickReply(reply.lowercase())
                            }
                        },
                        enabled = isInputEnabled,
                        shape = RoundedCornerShape(100.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    ) {
                        Text(reply)
                    }
                }
            }
        }
    }
}

@Composable
private fun AnswerLoadingIndicator(modifier: Modifier = Modifier) {
    var dotCount by remember { mutableIntStateOf(1) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(360)
            dotCount = if (dotCount == 3) 1 else dotCount + 1
        }
    }

    Row(
        modifier = modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = ".".repeat(dotCount),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
