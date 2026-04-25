package com.example.moasis.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.moasis.presentation.EmergencyViewModel
import com.example.moasis.presentation.EmergencyViewModelFactory
import com.example.moasis.presentation.ScreenMode
import com.example.moasis.presentation.UiAction
import com.example.moasis.ui.screen.ActiveProtocolScreen
import com.example.moasis.ui.screen.HomeScreen

@Composable
fun EmergencyApp(
    modifier: Modifier = Modifier,
    factory: EmergencyViewModelFactory,
) {
    val viewModel: EmergencyViewModel = viewModel(factory = factory)
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()

    Surface(modifier = modifier) {
        when (viewState.screenMode) {
            ScreenMode.HOME -> HomeScreen(
                isAiEnabled = viewState.isAiEnabled,
                onStart = viewModel::startEmergency,
            )

            ScreenMode.ACTIVE -> ActiveProtocolScreen(
                uiState = viewState.uiState,
                statusText = viewState.statusText,
                quickResponses = viewState.quickResponses,
                onSubmitText = viewModel::submitText,
                onAction = { action -> viewModel.reduce(com.example.moasis.presentation.AppEvent.UserTappedAction(action)) },
                onQuickResponse = viewModel::submitText,
            )
        }
    }
}
