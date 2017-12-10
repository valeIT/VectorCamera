package com.dozingcatsoftware.util

import java.io.FileNotFoundException
import java.lang.reflect.Method

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.media.MediaScannerConnection
import android.net.Uri
import android.view.View

object AndroidUtils {

    /** Adds a click listener to the given view which invokes the method named by methodName on the given target.
     * The method must be public and take no arguments.
     */
    fun bindOnClickListener(target: Any, view: View, methodName: String) {
        val method: Method
        try {
            method = target.javaClass.getMethod(methodName)
        } catch (ex: Exception) {
            throw IllegalArgumentException(ex)
        }

        view.setOnClickListener {
            try {
                method.invoke(target)
            } catch (ex: Exception) {
                throw RuntimeException(ex)
            }
        }
    }

    interface MediaScannerCallback {
        fun mediaScannerCompleted(scanPath: String, scanURI: Uri)
    }

    /** Notifies the OS to index the new image, so it shows up in Gallery. Allows optional callback method to notify client when
     * the scan is completed, e.g. so it can access the "content" URI that gets assigned.
     */
    @JvmOverloads
    fun scanSavedMediaFile(context: Context, path: String, callback: MediaScannerCallback? = null) {
        // silly array hack so closure can reference scannerConnection[0] before it's created
        val scannerConnection = arrayOfNulls<MediaScannerConnection>(1)
        try {
            val scannerClient = object : MediaScannerConnection.MediaScannerConnectionClient {
                override fun onMediaScannerConnected() {
                    scannerConnection[0]!!.scanFile(path, null)
                }

                override fun onScanCompleted(scanPath: String, scanURI: Uri) {
                    scannerConnection[0]!!.disconnect()
                    callback?.mediaScannerCompleted(scanPath, scanURI)
                }
            }
            scannerConnection[0] = MediaScannerConnection(context, scannerClient)
            scannerConnection[0]!!.connect()
        } catch (ignored: Exception) {
        }

    }

    /** Returns a BitmapFactory.Options object containing the size of the image at the given URI,
     * without actually loading the image.
     */
    @Throws(FileNotFoundException::class)
    fun computeBitmapSizeFromURI(context: Context, imageURI: Uri): BitmapFactory.Options {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeStream(context.contentResolver.openInputStream(imageURI), null, options)
        return options
    }

    /** Returns a Bitmap from the given URI that may be scaled by an integer factor to reduce its size,
     * while staying as least as large as the width and height parameters.
     */
    @Throws(FileNotFoundException::class)
    fun scaledBitmapFromURIWithMinimumSize(context: Context, imageURI: Uri, width: Int, height: Int): Bitmap {
        val options = computeBitmapSizeFromURI(context, imageURI)
        options.inJustDecodeBounds = false

        val wratio = 1.0f * options.outWidth / width
        val hratio = 1.0f * options.outHeight / height
        options.inSampleSize = Math.min(wratio, hratio).toInt()

        return BitmapFactory.decodeStream(context.contentResolver.openInputStream(imageURI), null, options)
    }

    /** Returns a Bitmap from the given URI that may be scaled by an integer factor to reduce its size,
     * so that its width and height are no greater than the corresponding parameters. The scale factor
     * will be a power of 2.
     */
    @Throws(FileNotFoundException::class)
    fun scaledBitmapFromURIWithMaximumSize(context: Context, imageURI: Uri, width: Int, height: Int): Bitmap {
        val options = computeBitmapSizeFromURI(context, imageURI)
        options.inJustDecodeBounds = false

        val wratio = powerOf2GreaterOrEqual(1.0 * options.outWidth / width)
        val hratio = powerOf2GreaterOrEqual(1.0 * options.outHeight / height)
        options.inSampleSize = Math.max(wratio, hratio)

        return BitmapFactory.decodeStream(context.contentResolver.openInputStream(imageURI), null, options)
    }

    internal fun powerOf2GreaterOrEqual(arg: Double): Int {
        if (arg < 0 && arg > 1 shl 31) throw IllegalArgumentException(arg.toString() + " out of range")
        var result = 1
        while (result < arg) result = result shl 1
        return result
    }

    /** Returns a scaled version of the given Bitmap. One of the returned Bitmap's width and height will be equal to size, and the other
     * dimension will be equal or less.
     */
    fun createScaledBitmap(bitmap: Bitmap, size: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        var scaledWidth = size
        var scaledHeight = size
        if (height < width) {
            scaledHeight = (size.toFloat() * 1.0f * height.toFloat() / width).toInt()
        } else if (width < height) {
            scaledWidth = (size.toFloat() * 1.0f * width.toFloat() / height).toInt()
        }
        return Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, false)
    }

    /** Given a width and height, fills output array with scaled width and height values
     * such that one of the values is exactly equal to the given maximum width or height,
     * and the other value is less than or equal to the maximum.
     */
    fun getScaledWidthAndHeightToMaximum(
            width: Int, height: Int, maxWidth: Int, maxHeight: Int, output: IntArray) {
        output[0] = width
        output[1] = height
        // common cases: if one dimension fits exactly and the other is smaller, return unmodified
        if (width == maxWidth && height <= maxHeight) return
        if (height == maxHeight && width <= maxWidth) return
        val wratio = width.toFloat() / maxWidth
        val hratio = height.toFloat() / maxHeight
        if (wratio <= hratio) {
            // scale to full height, partial width
            output[0] = (width / hratio).toInt()
            output[1] = maxHeight
        } else {
            // scale to full width, partial height
            output[0] = maxWidth
            output[1] = (height / wratio).toInt()
        }
    }

    fun scaledWidthAndHeightToMaximum(width: Int, height: Int, maxWidth: Int, maxHeight: Int): IntArray {
        val output = IntArray(2)
        getScaledWidthAndHeightToMaximum(width, height, maxWidth, maxHeight, output)
        return output
    }

    /** Returns an array which partitions the interval [0, max) into n approximately equal integer segments.
     * The returned array will have n+1 elements, with the first always 0 and the last always max.
     * If max=100 and n=3, the returned array will be [0, 33, 67, 100] representing the intervals
     * [0, 33), [33, 67), and [67, 100).
     */
    fun equalIntPartitions(max: Int, n: Int): IntArray {
        val result = IntArray(n + 1)
        result[0] = 0
        for (i in 1 until n) {
            val `val` = 1.0f * i.toFloat() * max.toFloat() / n
            result[i] = Math.round(`val`)
        }
        result[n] = max
        return result
    }

    /** On API level 14 (ICS) or higher, calls view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE)
     * and returns true. On earlier API versions, does nothing and returns false.
     */
    fun setSystemUiLowProfile(view: View): Boolean {
        return setSystemUiVisibility(view, "SYSTEM_UI_FLAG_LOW_PROFILE")
    }

    internal fun setSystemUiVisibility(view: View, flagName: String): Boolean {
        try {
            val setUiMethod = View::class.java.getMethod("setSystemUiVisibility", Int::class.javaPrimitiveType)
            val flagField = View::class.java.getField(flagName)
            setUiMethod.invoke(view, flagField.get(null))
            return true
        } catch (ex: Exception) {
            return false
        }

    }

    /** Returns the estimated memory usage in bytes for a bitmap. Calls bitmap.getByteCount() if that method
     * is available (in API level 12 or higher), otherwise returns 4 times the number of pixels in the bitmap.
     */
    fun getBitmapByteCount(bitmap: Bitmap): Int {
        try {
            val byteCountMethod = Bitmap::class.java.getMethod("getByteCount")
            return byteCountMethod.invoke(bitmap) as Int
        } catch (ex: Exception) {
            return 4 * bitmap.width * bitmap.height
        }

    }

    /** Enables or disables hardware acceleration for a view, if supported by the API. Returns true if successful  */
    fun setViewHardwareAcceleration(view: View, enabled: Boolean): Boolean {
        try {
            val setLayerType = View::class.java.getMethod("setLayerType", Int::class.javaPrimitiveType, Paint::class.java)
            val fieldName = if (enabled) "LAYER_TYPE_HARDWARE" else "LAYER_TYPE_SOFTWARE"
            val layerType = View::class.java.getField(fieldName).get(null) as Int
            setLayerType.invoke(view, layerType, null)
            return true
        } catch (ignored: Exception) {
            return false
        }

    }
}