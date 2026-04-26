package com.example.moasis.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.moasis.ui.component.OasisWordmark

@Composable
fun AiModelLoadingScreen(
    aiStatusText: String?,
    aiProgress: Float?,
    aiDownloadedBytes: Long?,
    aiModelLabel: String?,
    aiRouteText: String?,
    embeddingStatusText: String?,
    isEmbeddingEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center,
    ) {
        OasisWordmark(
            textStyle = MaterialTheme.typography.headlineMedium,
            textColor = MaterialTheme.colorScheme.onBackground,
            dotSize = 10.dp,
            spacing = 4.dp,
            dotYOffset = 2.dp,
        )
        Text(
            text = "Preparing offline AI model",
            modifier = Modifier.padding(top = 24.dp),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = aiStatusText ?: "Checking local runtime and model files.",
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        aiModelLabel?.let {
            Text(
                text = "Model: $it",
                modifier = Modifier.padding(top = 24.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        aiRouteText?.let {
            Text(
                text = "Route: $it",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isEmbeddingEnabled) {
            Text(
                text = "Embedding: ${embeddingStatusText ?: "Preparing local embedding model."}",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        aiDownloadedBytes?.takeIf { it > 0 }?.let {
            Text(
                text = "Downloaded: ${it.toReadableByteCount()}",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        aiProgress?.let {
            LinearProgressIndicator(
                progress = { it },
                modifier = Modifier
                    .padding(top = 20.dp)
                    .fillMaxWidth(),
            )
            Text(
                text = "${(it * 100).toInt()}%",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
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
