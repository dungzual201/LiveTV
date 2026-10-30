package com.livetv.vn.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.livetv.vn.ui.theme.LiveColors

@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    accent: Color = LiveColors.Live,
    mark: Color = LiveColors.Text,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val radius = CornerRadius(w * 0.26f, w * 0.26f)
        drawRoundRect(
            color = mark.copy(alpha = 0.08f),
            topLeft = Offset(0f, 0f),
            size = Size(w, h),
            cornerRadius = radius,
        )
        drawRoundRect(
            color = mark.copy(alpha = 0.32f),
            topLeft = Offset(w * 0.02f, h * 0.02f),
            size = Size(w * 0.96f, h * 0.96f),
            cornerRadius = radius,
            style = Stroke(width = w * 0.018f),
        )
        val path = Path().apply {
            moveTo(w * 0.40f, h * 0.31f)
            lineTo(w * 0.72f, h * 0.50f)
            lineTo(w * 0.40f, h * 0.69f)
            close()
        }
        drawPath(path, color = mark)
        drawCircle(
            color = accent,
            radius = w * 0.055f,
            center = Offset(w * 0.79f, h * 0.25f),
        )
    }
}
