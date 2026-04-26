package com.example.moasis.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.moasis.presentation.ChatMessage
import com.example.moasis.presentation.EmergencyViewState
import com.example.moasis.presentation.ScreenMode
import com.example.moasis.presentation.UiAction
import com.example.moasis.ui.component.AssistantHistoryBubble
import com.example.moasis.ui.component.AttachedImageStrip
import com.example.moasis.ui.component.ChatGreeting
import com.example.moasis.ui.component.ChatTopBar
import com.example.moasis.ui.component.Composer
import com.example.moasis.ui.component.CurrentStepBlock
import com.example.moasis.ui.component.NewSessionMenu
import com.example.moasis.ui.component.SessionsDrawer
import com.example.moasis.ui.component.SettingsSheet
import com.example.moasis.ui.component.UserBubble

@Composable
fun ChatScreen(
    viewState: EmergencyViewState,
    onSubmitText: (String) -> Unit,
    onResetSession: () -> Unit,
    onOpenSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onOfflineModeChange: (Boolean) -> Unit,
    onVoiceInput: () -> Unit,
    onPickImage: () -> Unit,
    onCaptureImage: () -> Unit,
    onClearImages: () -> Unit,
    onRemoveImage: (String) -> Unit,
    onAction: (UiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isSessionsDrawerOpen by remember { mutableStateOf(false) }
    var isSettingsSheetOpen by remember { mutableStateOf(false) }
    var isNewSessionMenuOpen by remember { mutableStateOf(false) }
    var composerText by remember { mutableStateOf("") }
    val isInputLockedForAi =
        (viewState.isAiEnabled && !viewState.isAiReady) ||
            (viewState.isEmbeddingEnabled && !viewState.isEmbeddingReady)

    val listState = rememberLazyListState()
    val historySize = viewState.chatHistory.size
    LaunchedEffect(historySize) {
        if (historySize > 0) {
            listState.scrollToItem(historySize)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        // Main content column
        Column(modifier = Modifier.fillMaxSize()) {
            ChatTopBar(
                onMenuClick = { isSessionsDrawerOpen = true },
                onNewSessionClick = { isNewSessionMenuOpen = true },
            )

            // Chat list
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
                contentPadding = PaddingValues(
                    top = 8.dp,
                    bottom = if (viewState.isViewingArchivedSession) 48.dp else 8.dp,
                ),
            ) {
                when (viewState.screenMode) {
                    ScreenMode.HOME -> {
                        item {
                            ChatGreeting(
                                onSuggestionPick = onSubmitText,
                                isEnabled = !isInputLockedForAi,
                            )
                        }
                    }
                    ScreenMode.ACTIVE -> {
                        itemsIndexed(
                            items = viewState.chatHistory,
                            key = { index, _ -> index },
                        ) { _, message ->
                            when (message) {
                                is ChatMessage.User -> UserBubble(text = message.text)
                                is ChatMessage.Assistant -> AssistantHistoryBubble(message = message)
                            }
                        }

                        if (!viewState.isViewingArchivedSession) {
                            item {
                                CurrentStepBlock(
                                    uiState = viewState.uiState,
                                    quickReplies = viewState.quickResponses,
                                    isInputEnabled = !isInputLockedForAi,
                                    onQuickReply = onSubmitText,
                                    onAction = onAction,
                                )
                            }
                        }
                    }
                }
            }

            // Attached images above composer
            if (!viewState.isViewingArchivedSession && viewState.attachedImagePaths.isNotEmpty()) {
                AttachedImageStrip(
                    imagePaths = viewState.attachedImagePaths,
                    onClearImages = onClearImages,
                    onRemoveImage = onRemoveImage,
                    canRemoveImages = !isInputLockedForAi,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }

            // Composer
            if (!viewState.isViewingArchivedSession) {
                Composer(
                    value = composerText,
                    onValueChange = { composerText = it },
                    isInputEnabled = !isInputLockedForAi,
                    isVoiceActive = viewState.uiState.isListening,
                    transcript = viewState.transcriptDraft,
                    onMic = onVoiceInput,
                    onTakePhoto = onCaptureImage,
                    onPickFromGallery = onPickImage,
                    onSettings = { isSettingsSheetOpen = true },
                    onSend = {
                        if (composerText.isNotBlank()) {
                            onSubmitText(composerText)
                            composerText = ""
                        }
                    },
                )
            }
        }

        // Overlays

        // Sessions drawer — slides in from left
        AnimatedVisibility(
            visible = isSessionsDrawerOpen,
            enter = fadeIn(animationSpec = tween(120)),
            exit = fadeOut(animationSpec = tween(140)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = { isSessionsDrawerOpen = false }),
            )
        }
        AnimatedVisibility(
            visible = isSessionsDrawerOpen,
            enter = slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = tween(240),
            ) + fadeIn(animationSpec = tween(120)),
            exit = slideOutHorizontally(
                targetOffsetX = { -it },
                animationSpec = tween(190),
            ) + fadeOut(animationSpec = tween(140)),
        ) {
            SessionsDrawer(
                isActiveSession = viewState.screenMode == ScreenMode.ACTIVE && !viewState.isViewingArchivedSession,
                earlierSessions = viewState.earlierSessions,
                onOpenSession = { sessionId ->
                    isSessionsDrawerOpen = false
                    onOpenSession(sessionId)
                },
                onNewSession = {
                    isSessionsDrawerOpen = false
                    onResetSession()
                },
                onDeleteSession = { sessionId ->
                    if (viewState.viewingArchivedSessionId == sessionId) {
                        isSessionsDrawerOpen = false
                    }
                    onDeleteSession(sessionId)
                },
                onClose = { isSessionsDrawerOpen = false },
            )
        }

        // Settings sheet — slides up from bottom
        AnimatedVisibility(
            visible = isSettingsSheetOpen,
            enter = fadeIn(animationSpec = tween(120)),
            exit = fadeOut(animationSpec = tween(140)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = { isSettingsSheetOpen = false }),
            )
        }
        AnimatedVisibility(
            visible = isSettingsSheetOpen,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(240),
            ) + fadeIn(animationSpec = tween(120)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(190),
            ) + fadeOut(animationSpec = tween(140)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            SettingsSheet(
                isOfflineModeEnabled = viewState.isOfflineModeEnabled,
                onOfflineModeChange = onOfflineModeChange,
                onClose = { isSettingsSheetOpen = false },
            )
        }

        // New session menu — top-right popover
        AnimatedVisibility(
            visible = isNewSessionMenuOpen,
            enter = fadeIn(animationSpec = tween(90)),
            exit = fadeOut(animationSpec = tween(120)),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(onClick = { isNewSessionMenuOpen = false }),
                )
            }
        }
        AnimatedVisibility(
            visible = isNewSessionMenuOpen,
            enter = slideInVertically(
                initialOffsetY = { -it / 3 },
                animationSpec = tween(170),
            ) + fadeIn(animationSpec = tween(100)),
            exit = slideOutVertically(
                targetOffsetY = { -it / 3 },
                animationSpec = tween(130),
            ) + fadeOut(animationSpec = tween(100)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 56.dp, end = 8.dp),
        ) {
            NewSessionMenu(
                onNewSession = {
                    isNewSessionMenuOpen = false
                    onResetSession()
                },
                onDismiss = { isNewSessionMenuOpen = false },
            )
        }

    }
}
