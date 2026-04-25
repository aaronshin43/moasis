package com.example.moasis.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.moasis.R

@Composable
fun ChatTopBar(
    onMenuClick: () -> Unit,
    onNewSessionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(62.dp)
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMenuClick) {
            Icon(
                imageVector = Icons.Outlined.Menu,
                contentDescription = "Open sessions",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(id = R.drawable.oasis_wordmark_launch),
                contentDescription = "Oasis",
                modifier = Modifier
                    .height(37.dp)
                    .padding(top = 2.dp),
                contentScale = ContentScale.Fit,
            )
        }
        IconButton(onClick = onNewSessionClick) {
            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = "New session",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}
