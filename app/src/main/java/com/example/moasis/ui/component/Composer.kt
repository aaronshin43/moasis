package com.example.moasis.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.example.moasis.ui.theme.MainOutline

private val VoiceBlue = Color(0xFF4D6BFF)
private val VoiceBlueSoft = Color(0x334D6BFF)
private val VoiceBlueBright = Color(0xFF8EA4FF)
private val VoiceCyan = Color(0xFF8EDCFF)
private val VoiceRingFill = Color(0xFFF4F7FF)
private val AttachmentMenuShadow = Color(0x26192128)
private val ComposerShadow = Color(0x331B2430)

@Composable
fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    isVoiceActive: Boolean,
    transcript: String,
    onMic: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickFromGallery: () -> Unit,
    onSettings: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isAttachmentMenuOpen by remember { mutableStateOf(false) }
    var attachmentAnchorBounds by remember { mutableStateOf(IntRect.Zero) }
    val density = LocalDensity.current
    val voiceTransition = rememberInfiniteTransition(label = "voiceAura")
    val voicePulse by voiceTransition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.72f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "voicePulse",
    )
    val voiceWaveShift by voiceTransition.animateFloat(
        initialValue = -0.18f,
        targetValue = 0.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "voiceWaveShift",
    )
    val cardBg = if (isVoiceActive) Color(0xFFF7F9FF) else MaterialTheme.colorScheme.surface
    val borderColor = if (isVoiceActive) VoiceBlue else MainOutline
    val shadowColor = if (isVoiceActive) VoiceBlueSoft else ComposerShadow
    val composerShape = RoundedCornerShape(24.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 14.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = if (isVoiceActive) (10 + (4 * voicePulse)).dp else 16.dp,
                    shape = composerShape,
                    ambientColor = shadowColor,
                    spotColor = shadowColor,
                )
                .then(
                    if (isVoiceActive) {
                        Modifier.drawWithCache {
                            val glowStroke = Stroke(width = ((4.dp.toPx()) + (2.5.dp.toPx() * voicePulse)))
                            val lineStroke = Stroke(width = 1.7.dp.toPx())
                            val shift = size.width * voiceWaveShift
                            val cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx())
                            val animatedBrush = Brush.linearGradient(
                                colors = listOf(
                                    VoiceBlue.copy(alpha = 0.18f),
                                    VoiceBlueBright.copy(alpha = 0.78f),
                                    VoiceCyan.copy(alpha = 0.95f),
                                    VoiceBlueBright.copy(alpha = 0.78f),
                                    VoiceBlue.copy(alpha = 0.18f),
                                ),
                                start = androidx.compose.ui.geometry.Offset(x = shift - (size.width * 0.18f), y = 0f),
                                end = androidx.compose.ui.geometry.Offset(x = size.width + shift, y = size.height),
                            )
                            val glowBrush = Brush.linearGradient(
                                colors = listOf(
                                    VoiceBlueSoft.copy(alpha = 0.04f + (0.03f * voicePulse)),
                                    VoiceBlue.copy(alpha = 0.08f + (0.04f * voicePulse)),
                                    VoiceCyan.copy(alpha = 0.05f + (0.04f * voicePulse)),
                                ),
                                start = androidx.compose.ui.geometry.Offset(x = 0f, y = 0f),
                                end = androidx.compose.ui.geometry.Offset(x = size.width, y = size.height),
                            )
                            onDrawWithContent {
                                drawContent()
                                drawRoundRect(
                                    brush = glowBrush,
                                    cornerRadius = cornerRadius,
                                    style = glowStroke,
                                )
                                drawRoundRect(
                                    brush = animatedBrush,
                                    cornerRadius = cornerRadius,
                                    style = lineStroke,
                                )
                            }
                        }
                    } else {
                        Modifier
                    }
                )
                .clip(composerShape)
                .background(cardBg)
                .border(
                    width = if (isVoiceActive) 2.dp else 1.4.dp,
                    color = borderColor,
                    shape = composerShape,
                )
                .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Input line or voice transcript
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 22.dp)
                    .padding(start = 0.dp, end = 6.dp, bottom = 4.dp),
            ) {
                if (isVoiceActive) {
                    Text(
                        text = if (transcript.isNotBlank()) "\"$transcript\"" else "Listening — say what's happening",
                        style = TextStyle(
                            fontSize = 15.sp,
                            fontStyle = if (transcript.isNotBlank()) FontStyle.Italic else FontStyle.Normal,
                            color = if (transcript.isNotBlank()) VoiceBlue else VoiceBlue.copy(alpha = 0.72f),
                        ),
                    )
                } else {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { inner ->
                            if (value.isEmpty()) {
                                Text(
                                    text = "Describe what's happening",
                                    style = TextStyle(
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                )
                            }
                            inner()
                        },
                    )
                }
            }

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    IconButton(
                        onClick = { isAttachmentMenuOpen = !isAttachmentMenuOpen },
                        modifier = Modifier
                            .size(36.dp)
                            .onGloballyPositioned { coordinates ->
                                val bounds = coordinates.boundsInWindow()
                                attachmentAnchorBounds = IntRect(
                                    left = bounds.left.toInt(),
                                    top = bounds.top.toInt(),
                                    right = bounds.right.toInt(),
                                    bottom = bounds.bottom.toInt(),
                                )
                            },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = "Add attachment",
                            tint = if (isVoiceActive) VoiceBlue.copy(alpha = 0.72f) else MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                IconButton(
                    onClick = onSettings,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Tune,
                        contentDescription = "Settings",
                        tint = if (isVoiceActive) VoiceBlue.copy(alpha = 0.72f) else MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Box(modifier = Modifier.weight(1f))

                // Mic/send button
                val isSendMode = !isVoiceActive && value.isNotBlank()
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .then(
                            if (isVoiceActive) {
                                Modifier.drawWithCache {
                                    val glowStroke = Stroke(width = ((4.dp.toPx()) + (1.5.dp.toPx() * voicePulse)))
                                    val lineStroke = Stroke(width = 1.8.dp.toPx())
                                    val shift = size.width * voiceWaveShift
                                    val animatedBrush = Brush.linearGradient(
                                        colors = listOf(
                                            VoiceBlue.copy(alpha = 0.22f),
                                            VoiceBlueBright.copy(alpha = 0.78f),
                                            VoiceCyan.copy(alpha = 0.92f),
                                            VoiceBlueBright.copy(alpha = 0.78f),
                                            VoiceBlue.copy(alpha = 0.22f),
                                        ),
                                        start = androidx.compose.ui.geometry.Offset(x = shift - (size.width * 0.12f), y = 0f),
                                        end = androidx.compose.ui.geometry.Offset(x = size.width + shift, y = size.height),
                                    )
                                    val glowBrush = Brush.linearGradient(
                                        colors = listOf(
                                            VoiceBlueSoft.copy(alpha = 0.05f + (0.03f * voicePulse)),
                                            VoiceBlue.copy(alpha = 0.08f + (0.04f * voicePulse)),
                                            VoiceCyan.copy(alpha = 0.06f + (0.03f * voicePulse)),
                                        ),
                                        start = androidx.compose.ui.geometry.Offset.Zero,
                                        end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                                    )
                                    val cornerRadius = CornerRadius(size.minDimension / 2f, size.minDimension / 2f)
                                    onDrawWithContent {
                                        drawContent()
                                        drawRoundRect(
                                            brush = glowBrush,
                                            cornerRadius = cornerRadius,
                                            style = glowStroke,
                                        )
                                        drawRoundRect(
                                            brush = animatedBrush,
                                            cornerRadius = cornerRadius,
                                            style = lineStroke,
                                        )
                                    }
                                }
                            } else {
                                Modifier
                            }
                        )
                        .clip(CircleShape)
                        .background(if (isVoiceActive) VoiceRingFill else MaterialTheme.colorScheme.onBackground),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(
                        onClick = if (isSendMode) onSend else onMic,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = if (isSendMode) Icons.Outlined.Send else Icons.Outlined.Mic,
                            contentDescription = if (isVoiceActive) "Stop listening" else if (isSendMode) "Send" else "Start voice",
                            tint = if (isVoiceActive) VoiceBlue else Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }

        if (isAttachmentMenuOpen) {
            val popupPositionProvider = with(density) {
                val gapPx = -8.dp.roundToPx()
                val horizontalNudgePx = 57.dp.roundToPx()
                object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect,
                        windowSize: IntSize,
                        layoutDirection: LayoutDirection,
                        popupContentSize: IntSize,
                    ): IntOffset {
                        val anchorWidth = attachmentAnchorBounds.right - attachmentAnchorBounds.left
                        val centeredX =
                            attachmentAnchorBounds.left +
                                ((anchorWidth - popupContentSize.width) / 2) +
                                horizontalNudgePx
                        val x = centeredX.coerceIn(
                            0,
                            (windowSize.width - popupContentSize.width).coerceAtLeast(0),
                        )
                        val y = (attachmentAnchorBounds.top - popupContentSize.height - gapPx)
                            .coerceAtLeast(0)
                        return IntOffset(x = x, y = y)
                    }
                }
            }
            Popup(
                popupPositionProvider = popupPositionProvider,
                onDismissRequest = { isAttachmentMenuOpen = false },
                properties = PopupProperties(focusable = true),
            ) {
                Surface(
                    modifier = Modifier
                        .width(172.dp)
                        .shadow(
                            elevation = 20.dp,
                            shape = RoundedCornerShape(20.dp),
                            ambientColor = AttachmentMenuShadow,
                            spotColor = AttachmentMenuShadow,
                        )
                        .border(1.2.dp, MainOutline, RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 5.dp),
                    ) {
                        AttachmentMenuItem(
                            icon = Icons.Outlined.CameraAlt,
                            title = "Take photo",
                            onClick = {
                                isAttachmentMenuOpen = false
                                onTakePhoto()
                            },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 1.dp,
                            color = MainOutline,
                        )
                        AttachmentMenuItem(
                            icon = Icons.Outlined.PhotoLibrary,
                            title = "Gallery",
                            onClick = {
                                isAttachmentMenuOpen = false
                                onPickFromGallery()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 10.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}
