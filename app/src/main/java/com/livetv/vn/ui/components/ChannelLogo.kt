package com.livetv.vn.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.livetv.vn.data.model.Channel
import com.livetv.vn.ui.theme.LiveColors

@Composable
fun ChannelLogo(
    channel: Channel,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(size * 0.22f)
    val logo = channel.logo?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(Color(0xFF14181D))
            .border(1.dp, Color.White.copy(alpha = 0.05f), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (logo == null) {
            LogoFallback(name = channel.name, size = size)
        } else {
            LogoImage(logo = logo, name = channel.name, size = size)
        }
    }
}

@Composable
private fun LogoImage(logo: String, name: String, size: Dp) {
    val context = LocalContext.current
    val request = remember(logo) {
        ImageRequest.Builder(context)
            .data(logo)
            .crossfade(false)
            .size(96)
            .build()
    }
    val painter = rememberAsyncImagePainter(request)
    if (painter.state !is AsyncImagePainter.State.Success) {
        LogoFallback(name = name, size = size)
    }
    Image(
        painter = painter,
        contentDescription = "Logo $name",
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .padding(size * 0.09f),
    )
}

@Composable
private fun LogoFallback(name: String, size: Dp) {
    Text(
        text = initials(name),
        color = LiveColors.TextMuted,
        fontSize = (size.value * 0.28f).sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

private fun initials(name: String): String {
    val words = name.split(' ')
        .map { it.trim() }
        .filter { it.isNotEmpty() && it.uppercase() !in setOf("HD", "FHD", "TV", "KÊNH") }
    if (words.isEmpty()) return name.take(2).uppercase()
    if (words.size == 1) return words[0].take(3).uppercase()
    return (words[0].take(1) + words[1].take(1)).uppercase()
}
