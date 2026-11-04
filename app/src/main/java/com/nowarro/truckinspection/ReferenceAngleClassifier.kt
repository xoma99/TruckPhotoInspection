package com.nowarro.truckinspection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import kotlin.math.sqrt

data class AngleReference(val taskId: String, val imageNames: List<String>)
data class AngleMatch(val taskId: String?, val confidence: Int, val reliable: Boolean, val message: String)

object ReferenceAngleClassifier {
    private const val GRID = 4
    private const val BINS = 8
    private const val SIZE = 96

    fun classify(context: Context, photoPath: String, references: List<AngleReference>, expectedTaskId: String): AngleMatch {
        val photo = BitmapFactory.decodeFile(photoPath)
            ?: return AngleMatch(null, 0, false, "Не удалось прочитать фото для проверки ракурса")
        val query = descriptor(photo)
        photo.recycle()
        if (query.isEmpty()) return AngleMatch(null, 0, false, "Не удалось проанализировать ракурс")

        val scores = mutableListOf<Pair<String, Double>>()
        for (ref in references) {
            val vectors = ref.imageNames.mapNotNull { name ->
                try {
                    context.assets.open("reference_photos/$name").use { stream ->
                        val bmp = BitmapFactory.decodeStream(stream) ?: return@use null
                        val v = descriptor(bmp)
                        bmp.recycle()
                        v
                    }
                } catch (_: Exception) { null }
            }
            if (vectors.isNotEmpty()) {
                val score = vectors.maxOf { cosine(query, it) }
                scores += ref.taskId to score
            }
        }
        if (scores.isEmpty()) return AngleMatch(null, 0, false, "Нет доступных эталонов для сравнения")
        val ranked = scores.sortedByDescending { it.second }
        val best = ranked.first()
        val expected = scores.firstOrNull { it.first == expectedTaskId }?.second ?: 0.0
        val margin = if (ranked.size > 1) best.second - ranked[1].second else 1.0
        val confidence = (best.second.coerceIn(0.0, 1.0) * 100).toInt()
        // Conservative: only offer a likely match when similarity and separation are both meaningful.
        val reliable = best.second >= 0.72 && margin >= 0.035
        val message = when {
            !reliable -> "Ракурс не удалось определить уверенно — проверьте фото вручную"
            best.first == expectedTaskId -> "Похоже, выбран правильный ракурс"
            else -> "Возможно, снят другой ракурс: ${best.first}"
        }
        return AngleMatch(best.first, confidence, reliable, message)
    }

    private fun descriptor(source: Bitmap): FloatArray {
        val bmp = Bitmap.createScaledBitmap(source, SIZE, SIZE, true)
        val pixels = IntArray(SIZE * SIZE)
        bmp.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        bmp.recycle()
        val gray = FloatArray(pixels.size)
        for (i in pixels.indices) {
            val c = pixels[i]
            gray[i] = (0.299f * ((c shr 16) and 255) + 0.587f * ((c shr 8) and 255) + 0.114f * (c and 255)) / 255f
        }
        val hist = FloatArray(GRID * GRID * BINS)
        for (y in 1 until SIZE - 1) for (x in 1 until SIZE - 1) {
            val i = y * SIZE + x
            val gx = gray[i + 1] - gray[i - 1]
            val gy = gray[i + SIZE] - gray[i - SIZE]
            val mag = sqrt(gx * gx + gy * gy)
            var angle = Math.atan2(gy.toDouble(), gx.toDouble()).toFloat()
            if (angle < 0) angle += Math.PI.toFloat()
            val bin = ((angle / Math.PI.toFloat()) * BINS).toInt().coerceIn(0, BINS - 1)
            val cellX = (x * GRID / SIZE).coerceIn(0, GRID - 1)
            val cellY = (y * GRID / SIZE).coerceIn(0, GRID - 1)
            hist[(cellY * GRID + cellX) * BINS + bin] += mag
        }
        val norm = sqrt(hist.sumOf { (it * it).toDouble() }).toFloat().coerceAtLeast(1e-6f)
        return FloatArray(hist.size) { hist[it] / norm }
    }

    private fun cosine(a: FloatArray, b: FloatArray): Double {
        val n = minOf(a.size, b.size)
        var dot = 0.0
        for (i in 0 until n) dot += a[i] * b[i]
        return dot.coerceIn(0.0, 1.0)
    }
}
