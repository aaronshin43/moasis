package com.example.moasis.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import com.example.moasis.audio.AndroidSpeechRecognizer
import com.example.moasis.audio.AndroidTtsEngine
import com.example.moasis.audio.AudioController
import com.example.moasis.audio.VoiceEvent
import com.example.moasis.imaging.CameraCaptureManager
import com.example.moasis.imaging.GalleryPickerManager
import com.example.moasis.imaging.ImageInputController
import com.example.moasis.presentation.EmergencyViewModel
import com.example.moasis.presentation.EmergencyViewModelFactory
import com.example.moasis.presentation.ScreenMode
import com.example.moasis.ui.screen.ActiveProtocolScreen
import com.example.moasis.ui.screen.HomeScreen

@Composable
fun EmergencyApp(
    modifier: Modifier = Modifier,
    factory: EmergencyViewModelFactory,
) {
    val viewModel: EmergencyViewModel = viewModel(factory = factory)
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val imageInputController = remember(context) { ImageInputController(context) }
    val galleryPickerManager = remember { GalleryPickerManager() }
    val cameraCaptureManager = remember(context) { CameraCaptureManager(context) }
    val audioController = remember(context, viewModel) {
        lateinit var controller: AudioController
        val speechRecognizer = AndroidSpeechRecognizer(context) { event ->
            controller.handleVoiceEvent(event)
        }
        val ttsEngine = AndroidTtsEngine(
            context = context,
            onStarted = {
                controller.onSpeechStarted()
            },
            onCompleted = { utteranceId ->
                controller.onSpeechCompleted()
                viewModel.reduce(com.example.moasis.presentation.AppEvent.TtsCompleted(utteranceId))
            },
            onStopped = { reason ->
                controller.onSpeechStopped()
                viewModel.reduce(com.example.moasis.presentation.AppEvent.TtsInterrupted(reason))
            },
        )
        controller = AudioController(
            speechRecognizer = speechRecognizer,
            ttsEngine = ttsEngine,
            onVoiceEvent = { event ->
                when (event) {
                    VoiceEvent.ListeningStarted -> {
                        viewModel.updateListening(true)
                        viewModel.updateStatus("Listening for voice input.")
                    }
                    VoiceEvent.ListeningStopped -> viewModel.updateListening(false)
                    is VoiceEvent.PartialTranscript -> {
                        viewModel.reduce(com.example.moasis.presentation.AppEvent.VoiceTranscript(event.text, false))
                    }
                    is VoiceEvent.FinalTranscript -> {
                        viewModel.updateListening(false)
                        viewModel.reduce(com.example.moasis.presentation.AppEvent.VoiceTranscript(event.text, true))
                    }
                    is VoiceEvent.Error -> {
                        viewModel.updateListening(false)
                        viewModel.updateStatus(event.message)
                    }
                }
            },
            onSpeakingChanged = { isSpeaking ->
                viewModel.updateSpeaking(isSpeaking)
            },
        )
        controller
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            audioController.startListening()
        } else {
            viewModel.updateListening(false)
            viewModel.updateStatus("Microphone permission denied. Text input is still available.")
        }
    }
    var launchCameraCapture: ((Uri) -> Unit)? = null
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            val captureUri = cameraCaptureManager.createCaptureUri()
            launchCameraCapture?.invoke(captureUri)
        } else {
            viewModel.updateStatus("Camera permission denied. Gallery attachment is still available.")
        }
    }
    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val pickedUri = galleryPickerManager.handlePickedUri(uri)
        if (pickedUri != null) {
            attachImageFromUri(
                sourceUri = pickedUri,
                imageInputController = imageInputController,
                onAttached = viewModel::attachImage,
                onError = viewModel::updateStatus,
            )
        }
    }
    val cameraCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        if (!success) {
            cameraCaptureManager.clearPendingCapture()
            viewModel.updateStatus("Camera capture was cancelled.")
        } else {
            val pendingUri = cameraCaptureManager.getPendingCaptureUri()
            if (pendingUri != null) {
                attachImageFromUri(
                    sourceUri = pendingUri,
                    imageInputController = imageInputController,
                    onAttached = viewModel::attachImage,
                    onError = viewModel::updateStatus,
                )
            } else {
                viewModel.updateStatus("Camera image was not available.")
            }
            cameraCaptureManager.clearPendingCapture()
        }
    }
    launchCameraCapture = { uri -> cameraCaptureLauncher.launch(uri) }

    LaunchedEffect(viewState.screenMode, viewState.speechRequestKey) {
        if (viewState.screenMode == ScreenMode.ACTIVE && viewState.uiState.primaryInstruction.isNotBlank()) {
            audioController.speak(viewState.uiState.primaryInstruction)
        }
    }

    DisposableEffect(audioController) {
        onDispose {
            audioController.destroy()
        }
    }

    fun startListening() {
        val permissionState = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
        if (permissionState == PackageManager.PERMISSION_GRANTED) {
            audioController.startListening()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun openGalleryPicker() {
        galleryPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    fun captureImage() {
        val permissionState = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (permissionState == PackageManager.PERMISSION_GRANTED) {
            val captureUri = cameraCaptureManager.createCaptureUri()
            cameraCaptureLauncher.launch(captureUri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Surface(modifier = modifier) {
        when (viewState.screenMode) {
            ScreenMode.HOME -> HomeScreen(
                isAiEnabled = viewState.isAiEnabled,
                aiStatusText = viewState.aiStatusText,
                aiProgress = viewState.aiProgress,
                isAiPreparing = viewState.isAiPreparing,
                isAiReady = viewState.isAiReady,
                canRetryAiPreparation = viewState.canRetryAiPreparation,
                aiModelLabel = viewState.aiModelLabel,
                aiRouteText = viewState.aiRouteText,
                aiCacheSummaryText = viewState.aiCacheSummaryText,
                onStart = viewModel::startEmergency,
                onRetryAiPreparation = viewModel::retryAiPreparation,
                onVoiceInput = ::startListening,
                transcriptDraft = viewState.transcriptDraft,
                isListening = viewState.uiState.isListening,
            )

            ScreenMode.ACTIVE -> ActiveProtocolScreen(
                uiState = viewState.uiState,
                statusText = viewState.statusText,
                aiStatusText = viewState.aiStatusText,
                aiProgress = viewState.aiProgress,
                isAiPreparing = viewState.isAiPreparing,
                canRetryAiPreparation = viewState.canRetryAiPreparation,
                aiModelLabel = viewState.aiModelLabel,
                aiRouteText = viewState.aiRouteText,
                aiCacheSummaryText = viewState.aiCacheSummaryText,
                quickResponses = viewState.quickResponses,
                onSubmitText = viewModel::submitText,
                onAction = { action -> viewModel.reduce(com.example.moasis.presentation.AppEvent.UserTappedAction(action)) },
                onQuickResponse = viewModel::submitText,
                onVoiceInput = ::startListening,
                transcriptDraft = viewState.transcriptDraft,
                attachedImagePaths = viewState.attachedImagePaths,
                onPickImage = ::openGalleryPicker,
                onCaptureImage = ::captureImage,
                onClearImages = viewModel::clearPendingImages,
            )
        }
    }
}

private fun attachImageFromUri(
    sourceUri: Uri,
    imageInputController: ImageInputController,
    onAttached: (String) -> Unit,
    onError: (String?) -> Unit,
) {
    imageInputController.copyToInternalCache(sourceUri)
        .onSuccess(onAttached)
        .onFailure { error ->
            onError(error.message ?: "Image attachment failed.")
        }
}
