package com.dozingcatsoftware.boojiecam

import android.graphics.Bitmap
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.GZIPOutputStream

/**
 * Created by brian on 10/9/17.
 */
class PhotoLibrary(val rootDirectory: File) {

    val thumbnailDirectory = File(rootDirectory, "thumbnails")

    init {
        PHOTO_ID_FORMAT.timeZone = TimeZone.getTimeZone("UTC")
    }

    fun savePhoto(processedBitmap: ProcessedBitmap,
                  successFn: (String) -> Unit,
                  errorFn: (Exception) -> Unit) {
        try {
            Log.i(TAG, "savePhoto start")
            val sourceImage = processedBitmap.sourceImage
            val width = sourceImage.width()
            val height = sourceImage.height()

            val photoId = PHOTO_ID_FORMAT.format(Date(sourceImage.timestamp))
            val photoDir = File(rootDirectory, photoId)
            photoDir.mkdirs()

            val rawImageFile = File(photoDir, "image.gz")
            GZIPOutputStream(FileOutputStream(rawImageFile)).use({
                it.write(processedBitmap.yuvBytes)
            })
            val uncompressedSize = width * height + 2 * (width / 2) * (height / 2)
            val compressedSize = rawImageFile.length()
            val compressedPercent = Math.round(100.0 * compressedSize / uncompressedSize)
            Log.i(TAG, "Wrote $compressedSize bytes, compressed to $compressedPercent")

            val metadata = mapOf(
                    "width" to width,
                    "height" to height,
                    "xFlipped" to sourceImage.orientation.isXFlipped(),
                    "yFlipped" to sourceImage.orientation.isYFlipped(),
                    "timestamp" to sourceImage.timestamp
            )
            val json = JSONObject(metadata).toString(2)
            FileOutputStream(File(photoDir, "metadata.json")).use({
                it.write(json.toByteArray(Charsets.UTF_8))
            })

            // Write full size image and thumbnail.
            // TODO: Scan image with MediaConnectionScanner
            run {
                val resultBitmap = processedBitmap.renderBitmap(width, height)
                val pngOutputStream = FileOutputStream(File(rootDirectory, photoId + ".jpg"))
                pngOutputStream.use({
                    resultBitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
                })
            }
            run {
                thumbnailDirectory.mkdirs()
                val noMediaFile = File(thumbnailDirectory, ".nomedia")
                if (!noMediaFile.exists()) {
                    noMediaFile.createNewFile()
                }
                val thumbnailOutputStream =
                        FileOutputStream(File(thumbnailDirectory, photoId + ".jpg"))
                val thumbnailBitmap = processedBitmap.renderBitmap(thumbnailWidth, thumbnailHeight)
                thumbnailOutputStream.use({
                    thumbnailBitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
                })
            }

            successFn(photoId)
        }
        catch (ex: Exception) {
            errorFn(ex)
        }
    }

    companion object {
        val TAG = "PhotoLibrary"
        val PHOTO_ID_FORMAT = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS")
        val thumbnailWidth = 320
        val thumbnailHeight = 240
    }
}