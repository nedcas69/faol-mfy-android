package uz.bekobod.faolmfy.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

/**
 * Rasmni siqish — TZ F6.4 (1280 px, sifat 80, ~250 KB).
 *
 * Nima uchun kerak: tizim kamerasi 8–12 MP rasm beradi (3–6 MB). Mahalla
 * sharoitida 3G tez-tez uchraydi, shuning uchun yuborishdan oldin siqamiz.
 * 40 xodim x kuniga 5 rasm x 250 KB = 50 MB/kun — bu ko'tarsa bo'ladigan hajm.
 */
object ImageUtils {

    private const val TAG = "ImageUtils"

    fun photoDir(context: Context): File =
        File(context.filesDir, "photos").apply { if (!exists()) mkdirs() }

    fun newTempFile(context: Context): File =
        File(photoDir(context), "cam_${System.currentTimeMillis()}.jpg")

    /**
     * Rasmni joyida siqadi (kirish faylini o'chirib, o'rniga siqilganini qo'yadi).
     * Qaytaradi: yakuniy fayl hajmi baytda, xato bo'lsa null.
     */
    fun compressInPlace(
        context: Context,
        source: File,
        maxSide: Int = 1280,
        quality: Int = 80,
    ): Int? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                Log.w(TAG, "Rasm o'qilmadi: ${source.name}")
                return null
            }

            // Xotirani tejash uchun avval taxminan kichraytiramiz
            var sample = 1
            while (bounds.outWidth / sample > maxSide * 2 && bounds.outHeight / sample > maxSide * 2) {
                sample *= 2
            }

            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp = BitmapFactory.decodeFile(source.absolutePath, opts) ?: return null

            // Kameradan kelgan rasm ko'pincha aylantirilgan holda saqlanadi
            val rotation = readRotation(source)
            if (rotation != 0f) {
                val m = Matrix().apply { postRotate(rotation) }
                val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                if (rotated != bmp) { bmp.recycle(); bmp = rotated }
            }

            // Aniq o'lchamga keltiramiz
            val longSide = maxOf(bmp.width, bmp.height)
            if (longSide > maxSide) {
                val scale = maxSide.toFloat() / longSide
                val scaled = Bitmap.createScaledBitmap(
                    bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true
                )
                if (scaled != bmp) { bmp.recycle(); bmp = scaled }
            }

            val out = File(source.parentFile, "c_${source.name}")
            FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }
            bmp.recycle()

            if (!source.delete()) Log.w(TAG, "Asl fayl o'chmadi: ${source.name}")
            if (!out.renameTo(source)) {
                Log.w(TAG, "Fayl nomi o'zgarmadi, siqilganini ishlatamiz")
                return out.length().toInt()
            }
            source.length().toInt()
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Xotira yetmadi: ${e.message}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Siqish xatosi: ${e.message}")
            null
        }
    }

    private fun readRotation(file: File): Float = try {
        when (ExifInterface(file.absolutePath)
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } catch (e: Exception) {
        0f
    }

    /** EXIF dan koordinata (kamera yozgan bo'lsa). Ko'p telefonlarda bo'lmaydi. */
    fun readExifLatLon(file: File): Pair<Double, Double>? = try {
        val exif = ExifInterface(file.absolutePath)
        val ll = FloatArray(2)
        @Suppress("DEPRECATION")
        if (exif.getLatLong(ll)) ll[0].toDouble() to ll[1].toDouble() else null
    } catch (e: Exception) {
        null
    }

    fun readExifDateMillis(file: File): Long? = try {
        val exif = ExifInterface(file.absolutePath)
        exif.dateTimeOriginal ?: exif.dateTime
    } catch (e: Exception) {
        null
    }

    fun uriFor(context: Context, file: File): Uri =
        androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
}
