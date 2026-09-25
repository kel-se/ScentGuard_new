package com.example.scentguard.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.scentguard.data.model.ChartData
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ScentGuardChart(
    data: ChartData,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    minY: Float = 0f,
    maxY: Float = 2000f,
    unit: String = "ppm",
    metricTitle: String = "Odor Concentration",
    showThresholds: Boolean = false,
    thresholdWarn: Float = 1000f,
    thresholdDanger: Float = 1500f,
    isWeekly: Boolean = false,
    yAxisTicks: List<Float> = emptyList()
) {
    var selectedIndex by remember { mutableStateOf(-1) }

    val horizontalMargin = 12.dp
    val computedTicks = remember(minY, maxY, yAxisTicks) {
        if (yAxisTicks.isNotEmpty()) yAxisTicks
        else {
            val step = (maxY - minY) / 4f
            listOf(minY, minY + step, minY + step * 2, minY + step * 3, maxY)
        }
    }

    Column(modifier = modifier) {
        // Scrubbing Header Details
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                if (data.points.isNotEmpty() && selectedIndex != -1) {
                    val point = data.points[selectedIndex]
                    val formattedVal = if (unit == "°C") {
                        String.format(Locale.getDefault(), "%.1f °C", point.y)
                    } else {
                        "${point.y.roundToInt()} $unit"
                    }

                    val status = if (showThresholds) {
                        when {
                            point.y < thresholdWarn -> " (SAFE)"
                            point.y < thresholdDanger -> " (WARN)"
                            else -> " (DANGER)"
                        }
                    } else ""

                    val periodLabel = if (isWeekly) "Daily Avg at ${point.label}" else "at ${point.label}"

                    Text(
                        text = "$metricTitle: $formattedVal$status",
                        style = MaterialTheme.typography.titleMedium,
                        color = lineColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = periodLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (data.points.size > 1) {
                    Text(
                        text = if (isWeekly) "Daily averages across past 7 days" else "15-min snapshots across past 24 hours",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "Touch/scrub chart for exact points",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            // Y-Axis Labels
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(44.dp)
                    .padding(end = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                computedTicks.reversed().forEach { tickVal ->
                    val tickText = if (unit == "°C") {
                        "${tickVal.roundToInt()}°"
                    } else {
                        "${tickVal.roundToInt()}"
                    }
                    Text(
                        text = tickText,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Canvas & Plot Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                if (data.points.size < 2) {
                    Text(
                        text = if (data.points.isEmpty()) "Waiting for sensor data..." else "Collecting telemetry points...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                } else {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(data.points) {
                                detectTapGestures(
                                    onPress = { offset ->
                                        val width = size.width
                                        val marginPx = horizontalMargin.toPx()
                                        val effectiveWidth = width - 2 * marginPx
                                        val spacing = effectiveWidth / (data.points.size - 1)
                                        val index = ((offset.x - marginPx) / spacing).roundToInt().coerceIn(0, data.points.size - 1)
                                        selectedIndex = index
                                        tryAwaitRelease()
                                        selectedIndex = -1
                                    }
                                )
                            }
                    ) {
                        val width = size.width
                        val height = size.height
                        val marginPx = horizontalMargin.toPx()
                        val effectiveWidth = width - 2 * marginPx
                        val spacing = effectiveWidth / (data.points.size - 1)
                        
                        val scaleRange = (maxY - minY).coerceAtLeast(1f)
                        fun getY(valUnits: Float) = height - ((valUnits - minY) / scaleRange) * height

                        // 1. Draw Threshold Zones (Background) ONLY if showThresholds is true
                        if (showThresholds) {
                            val warnY = getY(thresholdWarn).coerceIn(0f, height)
                            val dangerY = getY(thresholdDanger).coerceIn(0f, height)

                            // SAFE Zone
                            drawRect(
                                color = Color(0xFF34C759).copy(alpha = 0.05f),
                                topLeft = Offset(0f, warnY),
                                size = androidx.compose.ui.geometry.Size(width, height - warnY)
                            )
                            // WARN Zone
                            drawRect(
                                color = Color(0xFFFF9500).copy(alpha = 0.05f),
                                topLeft = Offset(0f, dangerY),
                                size = androidx.compose.ui.geometry.Size(width, warnY - dangerY)
                            )
                            // DANGER Zone
                            drawRect(
                                color = Color(0xFFFF3B30).copy(alpha = 0.05f),
                                topLeft = Offset(0f, 0f),
                                size = androidx.compose.ui.geometry.Size(width, dangerY)
                            )

                            // Threshold Divider Lines
                            drawLine(
                                color = Color(0xFFFF9500).copy(alpha = 0.3f),
                                start = Offset(0f, warnY),
                                end = Offset(width, warnY),
                                strokeWidth = 1.dp.toPx()
                            )
                            drawLine(
                                color = Color(0xFFFF3B30).copy(alpha = 0.3f),
                                start = Offset(0f, dangerY),
                                end = Offset(width, dangerY),
                                strokeWidth = 1.dp.toPx()
                            )
                        } else {
                            // General Grid Lines for Non-Threshold Charts (e.g. Temperature)
                            computedTicks.forEach { tickVal ->
                                val tickY = getY(tickVal).coerceIn(0f, height)
                                drawLine(
                                    color = Color.Gray.copy(alpha = 0.08f),
                                    start = Offset(0f, tickY),
                                    end = Offset(width, tickY),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }
                        }

                        // 2. Build Curve & Fill Path
                        val path = Path()
                        val fillPath = Path()
                        
                        data.points.forEachIndexed { i, point ->
                            val x = marginPx + i * spacing
                            val y = getY(point.y).coerceIn(0f, height)
                            
                            if (i == 0) {
                                path.moveTo(x, y)
                                fillPath.moveTo(x, height)
                                fillPath.lineTo(x, y)
                            } else {
                                val prevX = marginPx + (i - 1) * spacing
                                val prevY = getY(data.points[i - 1].y).coerceIn(0f, height)
                                val cp1X = prevX + (x - prevX) / 2
                                path.cubicTo(cp1X, prevY, cp1X, y, x, y)
                                fillPath.cubicTo(cp1X, prevY, cp1X, y, x, y)
                            }
                            
                            if (i == data.points.size - 1) {
                                fillPath.lineTo(x, height)
                                fillPath.close()
                            }
                        }

                        // 3. Draw Area Fill
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    lineColor.copy(alpha = 0.15f),
                                    Color.Transparent
                                )
                            )
                        )

                        // 4. Draw Trend Line
                        drawPath(
                            path = path,
                            color = lineColor,
                            style = Stroke(width = 2.5.dp.toPx())
                        )
                        
                        // 5. Scrubbing Line & Dot Indicator
                        if (selectedIndex != -1) {
                            val scrubX = marginPx + selectedIndex * spacing
                            val scrubY = getY(data.points[selectedIndex].y).coerceIn(0f, height)
                            drawLine(
                                color = lineColor.copy(alpha = 0.5f),
                                start = Offset(scrubX, 0f),
                                end = Offset(scrubX, height),
                                strokeWidth = 1.dp.toPx()
                            )
                            drawCircle(
                                color = lineColor,
                                radius = 6.dp.toPx(),
                                center = Offset(scrubX, scrubY)
                            )
                        }
                    }
                }
            }
        }
        
        // X-Axis Labels Row
        if (data.points.size > 1) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(start = 44.dp, top = 8.dp)) {
                val availableWidth = maxWidth
                val minLabelWidth = 64.dp 
                val maxVisibleLabels = (availableWidth / minLabelWidth).toInt().coerceAtLeast(2)
                val step = (data.points.size / maxVisibleLabels).coerceAtLeast(1)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    data.points.filterIndexed { index, _ -> 
                        index % step == 0 || index == data.points.size - 1 
                    }.forEach { point ->
                        Text(
                            text = point.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}
