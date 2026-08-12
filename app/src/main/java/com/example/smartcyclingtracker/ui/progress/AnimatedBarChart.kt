package com.example.smartcyclingtracker.ui.progress

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartcyclingtracker.theme.ElectricGreen
import com.example.smartcyclingtracker.theme.TextPrimary
import com.example.smartcyclingtracker.theme.TextSecondary
import com.example.smartcyclingtracker.theme.VividCyan

@Composable
fun AnimatedBarChart(
    data: List<ChartBarData>,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return

    val maxValue = remember(data) { data.maxOfOrNull { it.value }?.takeIf { it > 0 } ?: 1f }
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 10.sp, textAlign = TextAlign.Center)
    val valueStyle = MaterialTheme.typography.labelMedium.copy(color = TextPrimary, fontSize = 12.sp, textAlign = TextAlign.Center)

    val animationProgress = remember { Animatable(0f) }
    
    LaunchedEffect(data) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
        )
    }

    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    val primaryColor = ElectricGreen
    val textSecondaryColor = TextSecondary
    
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .pointerInput(data) {
                detectTapGestures { offset ->
                    val totalItems = data.size
                    val spacing = size.width * 0.05f
                    val availableWidth = size.width - (spacing * (totalItems + 1))
                    val calculatedBarWidth = availableWidth / totalItems
                    val maxBarWidth = 48.dp.toPx()
                    val barWidth = calculatedBarWidth.coerceAtMost(maxBarWidth)
                    
                    val totalDrawnWidth = (barWidth * totalItems) + (spacing * (totalItems - 1))
                    val startOffset = (size.width - totalDrawnWidth) / 2f
                    
                    val clickX = offset.x
                    
                    var found = false
                    for (i in 0 until totalItems) {
                        val startX = startOffset + i * (barWidth + spacing)
                        val endX = startX + barWidth
                        if (clickX in startX..endX) {
                            selectedIndex = if (selectedIndex == i) null else i
                            found = true
                            break
                        }
                    }
                    if (!found) selectedIndex = null
                }
            }
    ) {
        val totalItems = data.size
        val spacing = size.width * 0.05f
        val availableWidth = size.width - (spacing * (totalItems + 1))
        val calculatedBarWidth = availableWidth / totalItems
        val maxBarWidth = 48.dp.toPx()
        val barWidth = calculatedBarWidth.coerceAtMost(maxBarWidth)
        
        val totalDrawnWidth = (barWidth * totalItems) + (spacing * (totalItems - 1))
        val startOffset = (size.width - totalDrawnWidth) / 2f
        
        // Draw baseline
        drawLine(
            color = textSecondaryColor.copy(alpha = 0.2f),
            start = Offset(0f, size.height - 40.dp.toPx()),
            end = Offset(size.width, size.height - 40.dp.toPx()),
            strokeWidth = 1.dp.toPx()
        )

        data.forEachIndexed { index, item ->
            val barHeight = (item.value / maxValue) * (size.height - 70.dp.toPx()) * animationProgress.value
            val xOffset = startOffset + index * (barWidth + spacing)
            val yOffset = size.height - 40.dp.toPx() - barHeight

            // Draw Bar
            val brush = if (selectedIndex == null || selectedIndex == index) {
                Brush.verticalGradient(
                    colors = listOf(primaryColor, VividCyan),
                    startY = yOffset,
                    endY = yOffset + barHeight
                )
            } else {
                Brush.verticalGradient(
                    colors = listOf(primaryColor.copy(alpha = 0.3f), VividCyan.copy(alpha = 0.3f)),
                    startY = yOffset,
                    endY = yOffset + barHeight
                )
            }

            drawRoundRect(
                brush = brush,
                topLeft = Offset(xOffset, yOffset),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )

            // Draw Label (bottom)
            val labelResult = textMeasurer.measure(item.label, style = labelStyle)
            drawText(
                textLayoutResult = labelResult,
                topLeft = Offset(
                    xOffset + (barWidth - labelResult.size.width) / 2f,
                    size.height - 30.dp.toPx()
                )
            )

            // Draw Tooltip (top) if selected
            if (selectedIndex == index) {
                val valueText = "%.1f".format(item.value)
                val valueResult = textMeasurer.measure(valueText, style = valueStyle)
                drawText(
                    textLayoutResult = valueResult,
                    topLeft = Offset(
                        xOffset + (barWidth - valueResult.size.width) / 2f,
                        yOffset - valueResult.size.height - 8.dp.toPx()
                    )
                )
            }
        }
    }
}
