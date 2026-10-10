package com.ejden.app.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Photos des produits (data:image/...;base64,...) décodées en petit format, puis gardées en mémoire. */
object PhotoCache {
    private val cache = object : LruCache<String, Bitmap>(16 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    private fun keyOf(dataUri: String, target: Int): String =
        dataUri.length.toString() + ":" + dataUri.hashCode() + ":" + target

    fun peek(dataUri: String, target: Int): Bitmap? = cache.get(keyOf(dataUri, target))

    fun load(dataUri: String, target: Int): Bitmap? {
        val key = keyOf(dataUri, target)
        cache.get(key)?.let { return it }

        val comma = dataUri.indexOf(',')
        if (comma < 0) return null
        val bytes = try {
            Base64.decode(dataUri.substring(comma + 1), Base64.DEFAULT)
        } catch (e: IllegalArgumentException) {
            return null
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        cache.put(key, bitmap)
        return bitmap
    }
}

@Composable
fun rememberPhoto(dataUri: String?, targetPx: Int): ImageBitmap? {
    val state = produceState<ImageBitmap?>(
        initialValue = if (dataUri == null) null else PhotoCache.peek(dataUri, targetPx)?.asImageBitmap(),
        key1 = dataUri,
        key2 = targetPx
    ) {
        value = if (dataUri == null) {
            null
        } else {
            withContext(Dispatchers.Default) { PhotoCache.load(dataUri, targetPx)?.asImageBitmap() }
        }
    }
    return state.value
}
