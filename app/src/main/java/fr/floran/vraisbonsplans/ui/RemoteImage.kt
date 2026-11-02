package fr.floran.vraisbonsplans.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

private val bitmapCache = LruCache<String, Bitmap>(60)

/** Small image loader (no image library, per project conventions); shows a white box until loaded. */
@Composable
fun RemoteImage(url: String?, modifier: Modifier = Modifier) {
    var bitmap by remember(url) { mutableStateOf(url?.let { bitmapCache.get(it) }) }
    LaunchedEffect(url) {
        if (url == null || bitmap != null) return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) {
            runCatching { URL(url).openStream().use { BitmapFactory.decodeStream(it) } }.getOrNull()
        }?.also { bitmapCache.put(url, it) }
    }
    val loaded = bitmap
    if (loaded != null) {
        Image(loaded.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = modifier)
    } else {
        Box(modifier.background(Color.White))
    }
}
