package com.nowarro.truckinspection

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlin.math.max
import kotlin.math.min

data class PhotoQuality(
    val passed: Boolean,
    val score: Int,
    val message: String,
    val brightness: Double,
    val sharpness: Double
)

object PhotoQualityAnalyzer {
    /**
     * Lightweight on-device heuristic. It checks exposure and edge detail;
     * it does NOT identify the requested camera angle.
     */
    fun analyze(path: String): PhotoQuality {
        val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
        val bitmap = BitmapFactory.decodeFile(path, opts)
            ?: return PhotoQuality(false, 0, "Не удалось прочитать снимок", 0.0, 0.0)

        val w = bitmap.width
        val h = bitmap.height
        if (w < 3 || h < 3) {
            bitmap.recycle()
            return PhotoQuality(false, 0, "Слишком маленькое изображение", 0.0, 0.0)
        }

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        bitmap.recycle()

        val gray = IntArray(pixels.size)
        var sum = 0L
        var dark = 0
        var bright = 0
        for (i in pixels.indices) {
            val c = pixels[i]
            val y = (0.299 * ((c shr 16) and 255) +
                    0.587 * ((c shr 8) and 255) +
                    0.114 * (c and 255)).toInt()
            gray[i] = y
            sum += y
            if (y < 35) dark++
            if (y > 245) bright++
        }
        val brightness = sum.toDouble() / gray.size
        val darkRatio = dark.toDouble() / gray.size
        val brightRatio = bright.toDouble() / gray.size

        // Variance of a 4-neighbour Laplacian: a rough sharpness indicator.
        var lapSum = 0.0
        var lapSqSum = 0.0
        var count = 0
        for (y in 1 until h - 1 step 2) {
            for (x in 1 until w - 1 step 2) {
                val i = y * w + x
                val lap = (4 * gray[i] - gray[i - 1] - gray[i + 1] -
                        gray[i - w] - gray[i + w]).toDouble()
                lapSum += lap
                lapSqSum += lap * lap
                count++
            }
        }
        val mean = if (count > 0) lapSum / count else 0.0
        val sharpness = if (count > 0) max(0.0, lapSqSum / count - mean * mean) else 0.0

        val issues = mutableListOf<String>()
        if (brightness < 55 || darkRatio > 0.55) issues += "слишком тёмное фото"
        if (brightness > 215 || brightRatio > 0.35) issues += "возможен пересвет"
        if (sharpness < 90.0) issues += "возможно, снимок смазан или не в фокусе"

        val score = (
            100
                - (if (brightness < 55 || darkRatio > 0.55) 30 else 0)
                - (if (brightness > 215 || brightRatio > 0.35) 25 else 0)
                - (if (sharpness < 90.0) 45 else 0)
            ).coerceIn(0, 100)

        val passed = issues.isEmpty()
        val message = if (passed) "Качество снимка приемлемое" else issues.joinToString(", ").replaceFirstChar { it.uppercase() }
        return PhotoQuality(passed, score, message, brightness, sharpness)
    }
}
