package com.example.moasis.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Bloodtype
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.moasis.ui.theme.MainOutline

private data class Suggestion(val label: String, val icon: ImageVector, val query: String)

private val suggestions = listOf(
    Suggestion("Burn", Icons.Outlined.LocalFireDepartment, "I burned my arm"),
    Suggestion("Bleeding", Icons.Outlined.Bloodtype, "There is a bleeding wound"),
    Suggestion("Choking", Icons.Outlined.Air, "Someone is choking"),
    Suggestion("Collapsed", Icons.Outlined.Favorite, "My friend collapsed"),
)

@Composable
fun ChatGreeting(
    onSuggestionPick: (String) -> Unit,
    isEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "What's the\nemergency?",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            suggestions.forEach { suggestion ->
                OutlinedButton(
                    onClick = { onSuggestionPick(suggestion.query) },
                    enabled = isEnabled,
                    shape = RoundedCornerShape(100.dp),
                    contentPadding = PaddingValues(start = 14.dp, end = 18.dp, top = 10.dp, bottom = 10.dp),
                    border = BorderStroke(1.4.dp, MainOutline),
                ) {
                    Icon(
                        imageVector = suggestion.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 10.dp),
                    )
                    Text(
                        text = suggestion.label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
    }
}
