package com.ejden.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import com.ejden.app.data.Limits
import com.ejden.app.data.isSafePhoto
import java.io.ByteArrayOutputStream

class PhotoException(message: String) : Exception(message)

/** Prépare une photo de produit : lecture, rotation, réduction, JPEG (data:image/jpeg;base64,...). */
object PhotoPrep {
    private const val MAX_SOURCE_BYTES = 15L * 1024 * 1024

    // (taille maximale, qualité) : on réduit jusqu'à ce que la photo tienne dans la limite.
    private val STEPS = listOf(600 to 80, 480 to 70, 360 to 60)

    fun prepare(context: Context, uri: Uri): String {
        val resolver = context.contentResolver

        val type = resolver.getType(uri)
        if (type != null && !type.startsWith("image/")) {
            throw PhotoException("Ce fichier n'est pas une image.")
        }

        val size = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        if (size > MAX_SOURCE_BYTES) throw PhotoException("Cette photo est trop lourde.")

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw PhotoException("Image invalide.")

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 1200 && bounds.outHeight / (sample * 2) >= 1200) {
            sample *= 2
        }

        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw PhotoException("Image invalide.")

        val orientation = try {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val image = rotate(decoded, orientation)

        for ((maxSize, quality) in STEPS) {
            val ratio = minOf(1.0, maxSize.toDouble() / maxOf(image.width, image.height))
            val width = maxOf(1, Math.round(image.width * ratio).toInt())
            val height = maxOf(1, Math.round(image.height * ratio).toInt())

            // Fond blanc : le JPEG n'a pas de transparence.
            val canvasBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(canvasBitmap)
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(image, null, Rect(0, 0, width, height), Paint(Paint.FILTER_BITMAP_FLAG))

            val out = ByteArrayOutputStream()
            canvasBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            val result = "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)

            if (isSafePhoto(result)) return result
        }

        throw PhotoException("Photo trop grande (limite ${Math.round(Limits.PHOTO_MAX / 1000.0)} Ko).")
    }

    private fun rotate(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
