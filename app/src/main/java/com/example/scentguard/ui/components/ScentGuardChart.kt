package com.example.scentguard.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.scentguard.data.model.ChartData
import kotlin.math.roundToInt

@Composable
fun ScentGuardChart(
    data: ChartData,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    warnThreshold: Float = 1000f,
    dangerThreshold: Float = 1500f
) {
    var selectedIndex by remember { mutableStateOf(-1) }

    // Threshold boundaries
    val safeLimit = warnThreshold
    val dangerLimit = dangerThreshold
    
    // Dynamic max PPM based on thresholds and maximum data point
    val maxDataPoint = data.points.maxOfOrNull { it.y } ?: 0f
    val maxPpm = maxOf(2000f, dangerLimit * 1.25f, maxDataPoint * 1.15f)
    
    val horizontalMargin = 12.dp

    Column(modifier = modifier) {
        // Chart Header & Inspection Details
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Odor Concentration Trend",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (data.points.isNotEmpty() && selectedIndex != -1) {
                    val point = data.points[selectedIndex]
                    val status = when {
                        point.y < safeLimit -> "SAFE"
                        point.y < dangerLimit -> "WARN"
                        else -> "DANGER"
                    }
                    val statusColor = when (status) {
                        "SAFE" -> Color(0xFF34C759)
                        "WARN" -> Color(0xFFFF9500)
                        else -> Color(0xFFFF3B30)
                    }
                    Text(
                        text = "${point.y.roundToInt()} ppm ($status) at ${point.label}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = statusColor,
                        fontWeight = FontWeight.Bold
                    )
                } else if (data.points.size > 1) {
                    Text(
                        "Tap or drag along line to inspect exact readings",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // Main Chart Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            contentAlignment = Alignment.Center
        ) {
            if (data.points.size < 2) {
                Text(
                    text = if (data.points.isEmpty()) "Waiting for sensor data..." else "Collecting more data points...",
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
                    
                    val minVal = 0f
                    val scaleRange = maxPpm - minVal
                    
                    fun getY(ppm: Float) = height - ((ppm - minVal) / scaleRange) * height

                    // 1. Draw Threshold Zones (Background Tints)
                    // SAFE Zone (Green tint)
                    drawRect(
                        color = Color(0xFF34C759).copy(alpha = 0.06f),
                        topLeft = Offset(0f, getY(safeLimit)),
                        size = Size(width, height - getY(safeLimit))
                    )
                    // WARN Zone (Orange tint)
                    drawRect(
                        color = Color(0xFFFF9500).copy(alpha = 0.06f),
                        topLeft = Offset(0f, getY(dangerLimit)),
                        size = androidx.compose.ui.geometry.Size(width, getY(safeLimit) - getY(dangerLimit))
                    )
                    // DANGER Zone (Red tint)
                    drawRect(
                        color = Color(0xFFFF3B30).copy(alpha = 0.06f),
                        topLeft = Offset(0f, 0f),
                        size = androidx.compose.ui.geometry.Size(width, getY(dangerLimit))
                    )

                    // 2. Draw Threshold Lines
                    val gridAlpha = 0.25f
                    drawLine(
                        color = Color(0xFFFF9500).copy(alpha = gridAlpha),
                        start = Offset(0f, getY(safeLimit)),
                        end = Offset(width, getY(safeLimit)),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = Color(0xFFFF3B30).copy(alpha = gridAlpha),
                        start = Offset(0f, getY(dangerLimit)),
                        end = Offset(width, getY(dangerLimit)),
                        strokeWidth = 1.dp.toPx()
                    )

                    // 3. Construct Smooth Cubic Bezier Line
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

                    // 4. Area Fill
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                lineColor.copy(alpha = 0.2f),
                                Color.Transparent
                            )
                        )
                    )

                    // 5. Trend Line
                    drawPath(
                        path = path,
                        color = lineColor,
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                    
                    // 6. Scrubbing Touch Indicator
                    if (selectedIndex != -1) {
                        val scrubX = marginPx + selectedIndex * spacing
                        val scrubY = getY(data.points[selectedIndex].y).coerceIn(0f, height)
                        drawLine(
                            color = lineColor.copy(alpha = 0.5f),
                            start = Offset(scrubX, 0f),
                            end = Offset(scrubX, height),
                            strokeWidth = 1.5.dp.toPx()
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
        
        // Time Axis Labels
        if (data.points.size > 1) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                val availableWidth = maxWidth
                val minLabelWidth = 72.dp 
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
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = 10.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Non-Technical Color Legend for Sensitivity Settings
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Chart Color Guide (Sensitivity Active)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ChartLegendItem(
                        color = Color(0xFF34C759),
                        label = "Safe (<${warnThreshold.toInt()} ppm)"
                    )
                    ChartLegendItem(
                        color = Color(0xFFFF9500),
                        label = "Warn (${warnThreshold.toInt()}–${dangerThreshold.toInt()} ppm)"
                    )
                    ChartLegendItem(
                        color = Color(0xFFFF3B30),
                        label = "Danger (>${dangerThreshold.toInt()} ppm)"
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartLegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
