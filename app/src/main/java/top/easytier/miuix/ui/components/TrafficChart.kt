package top.easytier.miuix.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

data class TrafficDataPoint(
    val txBytes: Long,
    val rxBytes: Long,
    val timestamp: Long,
)

@Composable
fun TrafficChart(
    txHistory: List<Long>,
    rxHistory: List<Long>,
    maxPoints: Int = 60,
    txColor: Color = Color(0xFF3482FF),
    rxColor: Color = Color(0xFF4CAF50),
    modifier: Modifier = Modifier,
) {
    val txData = txHistory.takeLast(maxPoints)
    val rxData = rxHistory.takeLast(maxPoints)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp),
    ) {
        val width = size.width
        val height = size.height
        val maxValue = (txData + rxData).maxOrNull()?.coerceAtLeast(1L) ?: 1L

        // Draw grid lines
        for (i in 0..4) {
            val y = height * i / 4
            drawLine(
                color = Color.Gray.copy(alpha = 0.2f),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f,
            )
        }

        // Draw TX line
        if (txData.size >= 2) {
            val path = Path()
            val stepX = width / (maxPoints - 1).coerceAtLeast(1)
            txData.forEachIndexed { index, value ->
                val x = index * stepX
                val y = height - (value.toFloat() / maxValue * height)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, txColor, style = Stroke(width = 2f))
        }

        // Draw RX line
        if (rxData.size >= 2) {
            val path = Path()
            val stepX = width / (maxPoints - 1).coerceAtLeast(1)
            rxData.forEachIndexed { index, value ->
                val x = index * stepX
                val y = height - (value.toFloat() / maxValue * height)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, rxColor, style = Stroke(width = 2f))
        }
    }
}
