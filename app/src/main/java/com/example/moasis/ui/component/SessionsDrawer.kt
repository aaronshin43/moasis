package com.example.moasis.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Bloodtype
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.moasis.domain.model.EmergencySessionSummary
import com.example.moasis.ui.theme.MainOutline
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DrawerBg = Color(0xFFEDE6D8)

@Composable
fun SessionsDrawer(
    isActiveSession: Boolean,
    earlierSessions: List<EmergencySessionSummary>,
    onOpenSession: (String) -> Unit,
    onNewSession: () -> Unit,
    onDeleteSession: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDeleteSession by remember { mutableStateOf<EmergencySessionSummary?>(null) }

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .width(300.dp)
                .fillMaxHeight()
                .statusBarsPadding()
                .background(DrawerBg),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "Sessions",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onBackground)
                }
            }

            // New session button
            Surface(
                onClick = onNewSession,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(100.dp),
                color = MaterialTheme.colorScheme.onBackground,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Text("New session", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = Color.White)
                }
            }

            // Session list
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                if (isActiveSession) {
                    DrawerSection(label = "Today") {
                        SessionRow(
                            icon = Icons.Outlined.LocalFireDepartment,
                            title = "Current session",
                            meta = "In progress",
                            active = true,
                            onClick = onClose,
                        )
                    }
                }

                DrawerSection(label = "Earlier") {
                    if (earlierSessions.isEmpty()) {
                        Text(
                            text = "Previous sessions will appear here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                        )
                    } else {
                        earlierSessions.forEach { session ->
                            SessionRow(
                                icon = sessionIcon(session.category),
                                title = session.title,
                                meta = sessionDateLabel(session),
                                onClick = { onOpenSession(session.sessionId) },
                                onDelete = { pendingDeleteSession = session },
                            )
                        }
                    }
                }
            }
        }

        pendingDeleteSession?.let { session ->
            AlertDialog(
                onDismissRequest = { pendingDeleteSession = null },
                title = { Text("Delete this session?") },
                text = {
                    Text("This will permanently remove \"${session.title}\" from your saved sessions.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            pendingDeleteSession = null
                            onDeleteSession(session.sessionId)
                        },
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteSession = null }) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}

private fun sessionIcon(category: String?): ImageVector {
    return when (category) {
        "burn" -> Icons.Outlined.LocalFireDepartment
        "bleeding" -> Icons.Outlined.Bloodtype
        "breathing", "choking", "drowning" -> Icons.Outlined.Air
        "cardiac", "chest_pain", "stroke" -> Icons.Outlined.Favorite
        "heat", "hypothermia" -> Icons.Outlined.Thermostat
        "electric_shock" -> Icons.Outlined.Bolt
        else -> Icons.Outlined.Favorite
    }
}

private fun sessionDateLabel(session: EmergencySessionSummary): String {
    val zone = ZoneId.systemDefault()
    val sessionDate = Instant.ofEpochMilli(session.updatedAtMs).atZone(zone).toLocalDate()
    return sessionDate.format(DateTimeFormatter.ofPattern("MMM d"))
}

@Composable
private fun DrawerSection(label: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = label.uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.6.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        )
        content()
    }
}

@Composable
private fun SessionRow(
    icon: ImageVector,
    title: String,
    meta: String,
    active: Boolean = false,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (active) MaterialTheme.colorScheme.surface else Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .border(width = 1.dp, color = MainOutline, shape = CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onBackground)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displaySessionTitle(title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground,
                    lineHeight = 17.sp,
                )
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (active) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            } else if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Delete session",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun displaySessionTitle(title: String): String {
    return when {
        title.endsWith(" basic care", ignoreCase = true) ->
            title.dropLast(" basic care".length).trimEnd() + "\nbasic care"
        title.length > 28 && " (" in title ->
            title.replace(" (", "\n(")
        else -> title
    }
}
