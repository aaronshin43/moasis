package com.example.moasis

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.example.moasis.data.protocol.AndroidAssetTextSource
import com.example.moasis.data.protocol.JsonProtocolDataSource
import com.example.moasis.data.protocol.ProtocolRepository
import com.example.moasis.data.visual.AssetCatalogDataSource
import com.example.moasis.data.visual.VisualAssetRepository
import com.example.moasis.domain.state.DialogueStateManager
import com.example.moasis.presentation.EmergencyViewModelFactory
import com.example.moasis.ui.EmergencyApp
import com.example.moasis.ui.theme.MoasisTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val assetTextSource = AndroidAssetTextSource(assets)
        val protocolRepository = ProtocolRepository(
            dataSource = JsonProtocolDataSource(assetTextSource),
        )
        val visualAssetRepository = VisualAssetRepository(
            protocolRepository = protocolRepository,
            assetCatalogDataSource = AssetCatalogDataSource(assetTextSource),
        )
        val viewModelFactory = EmergencyViewModelFactory(
            dialogueStateManager = DialogueStateManager(protocolRepository),
            protocolRepository = protocolRepository,
            visualAssetRepository = visualAssetRepository,
        )
        setContent {
            MoasisTheme {
                EmergencyApp(
                    modifier = Modifier.fillMaxSize(),
                    factory = viewModelFactory,
                )
            }
        }
    }
}
