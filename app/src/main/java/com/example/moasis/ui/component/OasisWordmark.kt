package com.example.moasis.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.moasis.ui.theme.EmergencyRed
import com.example.moasis.ui.theme.SlateText

@Composable
fun OasisWordmark(
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.titleLarge,
    textColor: Color = SlateText,
    dotColor: Color = EmergencyRed,
    dotSize: Dp = 10.dp,
    spacing: Dp = 3.dp,
    dotYOffset: Dp = 2.dp,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing),
    ) {
        Text(
            text = "Oasis",
            style = textStyle.copy(
                fontWeight = FontWeight.W700,
                letterSpacing = (-0.18).sp,
            ),
            color = textColor,
        )
        Box(
            modifier = Modifier
                .offset(y = dotYOffset)
                .size(dotSize)
                .clip(CircleShape)
                .background(dotColor),
        )
    }
}
