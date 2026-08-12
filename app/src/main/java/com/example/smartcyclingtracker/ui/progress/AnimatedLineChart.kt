package com.example.smartcyclingtracker.ui.progress

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput

import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartcyclingtracker.theme.ElectricGreen
import com.example.smartcyclingtracker.theme.TextPrimary
import com.example.smartcyclingtracker.theme.TextSecondary
import com.example.smartcyclingtracker.theme.VividCyan

@Composable
fun AnimatedLineChart(
    data: List<ChartBarData>,
    modifier: Modifier = Modifier,
    isArea: Boolean = false
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
                        val centerX = startOffset + i * (barWidth + spacing) + barWidth / 2f
                        if (kotlin.math.abs(clickX - centerX) < (barWidth + spacing) / 2f) {
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
        
        val bottomY = size.height - 40.dp.toPx()

        // Draw baseline
        drawLine(
            color = TextSecondary.copy(alpha = 0.2f),
            start = Offset(0f, bottomY),
            end = Offset(size.width, bottomY),
            strokeWidth = 1.dp.toPx()
        )
        
        if (data.size > 1) {
            val strokePath = Path()
            val points = mutableListOf<Offset>()
            
            data.forEachIndexed { index, item ->
                val barHeight = (item.value / maxValue) * (size.height - 70.dp.toPx()) * animationProgress.value
                val centerX = startOffset + index * (barWidth + spacing) + barWidth / 2f
                val y = bottomY - barHeight
                points.add(Offset(centerX, y))
                
                if (index == 0) {
                    strokePath.moveTo(centerX, y)
                } else {
                    strokePath.lineTo(centerX, y)
                }
            }
            
            if (isArea) {
                val fillPath = Path()
                fillPath.addPath(strokePath)
                fillPath.lineTo(points.last().x, bottomY)
                fillPath.lineTo(points.first().x, bottomY)
                fillPath.close()
                
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(primaryColor.copy(alpha = 0.4f), Color.Transparent),
                        startY = 0f,
                        endY = bottomY
                    )
                )
            }
            
            drawPath(
                path = strokePath,
                brush = Brush.horizontalGradient(
                    colors = listOf(primaryColor, VividCyan)
                ),
                style = Stroke(
                    width = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
            
            // Draw points
            points.forEachIndexed { index, point ->
                val radius = if (selectedIndex == index) 6.dp.toPx() else 4.dp.toPx()
                drawCircle(
                    color = VividCyan,
                    radius = radius,
                    center = point
                )
            }
        } else if (data.size == 1) {
            // Draw single point
            val item = data[0]
            val barHeight = (item.value / maxValue) * (size.height - 70.dp.toPx()) * animationProgress.value
            val centerX = startOffset + barWidth / 2f
            val y = bottomY - barHeight
            
            val radius = if (selectedIndex == 0) 6.dp.toPx() else 4.dp.toPx()
            drawCircle(
                color = VividCyan,
                radius = radius,
                center = Offset(centerX, y)
            )
        }

        // Draw Labels and Tooltips
        data.forEachIndexed { index, item ->
            val barHeight = (item.value / maxValue) * (size.height - 70.dp.toPx()) * animationProgress.value
            val centerX = startOffset + index * (barWidth + spacing) + barWidth / 2f
            val yOffset = bottomY - barHeight

            val labelResult = textMeasurer.measure(item.label, style = labelStyle)
            drawText(
                textLayoutResult = labelResult,
                topLeft = Offset(
                    centerX - labelResult.size.width / 2f,
                    size.height - 30.dp.toPx()
                )
            )

            if (selectedIndex == index) {
                val valueText = "%.1f".format(item.value)
                val valueResult = textMeasurer.measure(valueText, style = valueStyle)
                drawText(
                    textLayoutResult = valueResult,
                    topLeft = Offset(
                        centerX - valueResult.size.width / 2f,
                        yOffset - valueResult.size.height - 12.dp.toPx()
                    )
                )
            }
        }
    }
}
