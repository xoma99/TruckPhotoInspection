package com.nowarro.truckinspection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

data class ModelPrediction(val label: String, val confidence: Float)

class TfliteAngleModel private constructor(
    private val interpreter: Interpreter,
    private val labels: List<String>
) : AutoCloseable {
    fun predict(imagePath: String): ModelPrediction? {
        val bitmap = BitmapFactory.decodeFile(imagePath) ?: return null
        val inputSize = interpreter.getInputTensor(0).shape()[1]
        val scaled = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        if (scaled !== bitmap) bitmap.recycle()
        val input = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4)
            .order(ByteOrder.nativeOrder())
        val pixels = IntArray(inputSize * inputSize)
        scaled.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        scaled.recycle()
        for (pixel in pixels) {
            input.putFloat((((pixel shr 16) and 255) / 127.5f) - 1f)
            input.putFloat((((pixel shr 8) and 255) / 127.5f) - 1f)
            input.putFloat(((pixel and 255) / 127.5f) - 1f)
        }
        input.rewind()
        val output = Array(1) { FloatArray(labels.size) }
        interpreter.run(input, output)
        val index = output[0].indices.maxByOrNull { output[0][it] } ?: return null
        return ModelPrediction(labels[index], output[0][index])
    }

    override fun close() = interpreter.close()

    companion object {
        fun loadIfAvailable(context: Context): TfliteAngleModel? {
            return try {
                val labels = context.assets.open("labels.json").bufferedReader().use { it.readText() }
                val parsed = Regex("\"([^\"]+)\"").findAll(labels).map { it.groupValues[1] }.toList()
                if (parsed.isEmpty()) return null
                val afd = context.assets.openFd("angle_classifier.tflite")
                val stream = FileInputStream(afd.fileDescriptor)
                val mapped = stream.channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
                val interpreter = Interpreter(mapped)
                TfliteAngleModel(interpreter, parsed)
            } catch (_: Exception) {
                null
            }
        }
    }
}
