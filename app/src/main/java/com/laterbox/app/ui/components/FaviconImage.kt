package com.laterbox.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.laterbox.app.ui.theme.LaterboxTheme

@Composable
fun FaviconImage(
    domain: String,
    faviconUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp
) {
    val context = LocalContext.current
    val initial = domain.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "•"
    val validUrl = faviconUrl?.takeIf {
        it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
    }
    var loadFailed by remember(validUrl) { mutableStateOf(false) }

    val request = remember(validUrl, context) {
        validUrl?.let { url ->
            ImageRequest.Builder(context)
                .data(url)
                .memoryCacheKey("favicon_$url")
                .diskCacheKey(url)
                .crossfade(true)
                .build()
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(4.dp))
            .background(LaterboxTheme.colors.surfaceSecondary)
            .border(
                width = LaterboxTheme.spacing.hairline,
                color = LaterboxTheme.colors.borderSubtle,
                shape = RoundedCornerShape(4.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (request != null && !loadFailed) {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { loadFailed = true },
                modifier = Modifier.size(size - 4.dp)
            )
        } else {
            Text(
                text = initial,
                fontSize = (size.value * 0.55).sp,
                fontWeight = FontWeight.SemiBold,
                color = LaterboxTheme.colors.textSecondary
            )
        }
    }
}
