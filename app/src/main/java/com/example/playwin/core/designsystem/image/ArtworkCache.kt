package com.example.playwin.core.designsystem.image

import android.content.res.Resources
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Raster artwork decoded once, off the main thread, and kept for the life of the
 * process. painterResource re-decodes a bitmap on the main thread every time a
 * screen is composed, which stalled the lobby (nine badges) and the Ludo board on
 * every visit. Everything cached here is pre-sized to its largest on-screen size,
 * so the whole set stays a few MB.
 */
object ArtworkCache {
    private val images = ConcurrentHashMap<Int, ImageBitmap>()

    /** The decoded image if it's already cached, without decoding. */
    fun peek(@DrawableRes id: Int): ImageBitmap? = images[id]

    /** Decodes [id] on the calling thread if it isn't cached yet. Keep this off the main thread. */
    fun load(resources: Resources, @DrawableRes id: Int): ImageBitmap =
        images.computeIfAbsent(id) { BitmapFactory.decodeResource(resources, it).asImageBitmap() }

    /** Warms the cache ahead of the screens that draw [ids]. */
    suspend fun preload(resources: Resources, ids: Iterable<Int>) = withContext(Dispatchers.IO) {
        ids.forEach { load(resources, it) }
    }
}

/** [id] from [ArtworkCache]; null for the frame or two before a not-yet-preloaded image is ready. */
@Composable
fun rememberArtwork(@DrawableRes id: Int): ImageBitmap? {
    val resources = LocalContext.current.resources
    val image by produceState(initialValue = ArtworkCache.peek(id), id) {
        if (value == null) value = withContext(Dispatchers.IO) { ArtworkCache.load(resources, id) }
    }
    return image
}

/**
 * Drop-in for painterResource backed by [ArtworkCache]. Until the image is ready it
 * paints nothing, so the space it's laid out in stays put rather than jumping.
 */
@Composable
fun artworkPainter(@DrawableRes id: Int): Painter {
    val image = rememberArtwork(id)
    return remember(image) { image?.let(::BitmapPainter) ?: ColorPainter(Color.Transparent) }
}
