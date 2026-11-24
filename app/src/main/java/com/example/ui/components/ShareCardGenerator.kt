package com.example.ui.components

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ShareCardGenerator {

    private val shareScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun shareAyahAsImageCard(
        context: Context,
        surahName: String,
        verseKey: String,
        arabicText: String,
        translationText: String,
        translatorName: String
    ) {
        val appContext = context.applicationContext
        shareScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    renderCardFile(
                        context = appContext,
                        surahName = surahName,
                        verseKey = verseKey,
                        arabicText = arabicText,
                        translationText = translationText,
                        translatorName = translatorName
                    )
                }
                val uri = FileProvider.getUriForFile(
                    appContext,
                    "${appContext.packageName}.fileprovider",
                    file
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, "$arabicText\n\n\"$translationText\" [$surahName $verseKey]")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(shareIntent, appContext.getString(R.string.share_as_card))
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                appContext.startActivity(chooser)
            } catch (e: Exception) {
                Toast.makeText(
                    appContext,
                    "Could not generate card: ${e.localizedMessage}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun renderCardFile(
        context: Context,
        surahName: String,
        verseKey: String,
        arabicText: String,
        translationText: String,
        translatorName: String
    ): File {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val isLowRam = am?.isLowRamDevice == true
        val width = if (isLowRam) 720 else 1080
        val height = if (isLowRam) 900 else 1350
        val scale = width / 1080f

        val bitmap = Bitmap.createBitmap(
            width,
            height,
            if (isLowRam) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
        )
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.parseColor("#FAF7F0"))

            val bgOptions = BitmapFactory.Options().apply {
                inSampleSize = if (isLowRam) 3 else 2
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val ornament = BitmapFactory.decodeResource(context.resources, R.drawable.img_share_card_ornament, bgOptions)
            if (ornament != null) {
                val srcRect = Rect(0, 0, ornament.width, ornament.height)
                val dstRect = Rect(0, 0, width, height)
                val alphaPaint = Paint().apply { alpha = 52 }
                canvas.drawBitmap(ornament, srcRect, dstRect, alphaPaint)
                ornament.recycle()
            }

            val borderPaint = Paint().apply {
                color = Color.parseColor("#0F5C4D")
                style = Paint.Style.STROKE
                strokeWidth = 6f * scale
            }
            canvas.drawRect(44f * scale, 44f * scale, width - 44f * scale, height - 44f * scale, borderPaint)

            val goldPaint = Paint().apply {
                color = Color.parseColor("#C89D54")
                style = Paint.Style.STROKE
                strokeWidth = 3f * scale
            }
            canvas.drawRect(58f * scale, 58f * scale, width - 58f * scale, height - 58f * scale, goldPaint)

            val arabicTypeface = ResourcesCompat.getFont(context, R.font.amiri)
            val latinTypeface = ResourcesCompat.getFont(context, R.font.plus_jakarta_sans)

            val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F5C4D")
                textSize = 36f * scale
                typeface = latinTypeface
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("$surahName • $verseKey", width / 2f, 150f * scale, headerPaint)

            val horizontalPad = (100f * scale).toInt()
            val textWidth = (width - horizontalPad * 2).coerceAtLeast(100)

            val arabicPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1E2421")
                textSize = 58f * scale
                typeface = arabicTypeface
            }
            val arabicLayout = StaticLayout.Builder
                .obtain(arabicText, 0, arabicText.length, arabicPaint, textWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(14f * scale, 1.25f)
                .build()

            val arabicY = 240f * scale
            canvas.save()
            canvas.translate(horizontalPad.toFloat(), arabicY)
            arabicLayout.draw(canvas)
            canvas.restore()

            val translationPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#35403B")
                textSize = 34f * scale
                typeface = latinTypeface
            }
            val translationY = (arabicY + arabicLayout.height + 70f * scale).coerceAtMost(height - 320f * scale)
            val translationLayout = StaticLayout.Builder
                .obtain(translationText, 0, translationText.length, translationPaint, textWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(10f * scale, 1.3f)
                .build()

            canvas.save()
            canvas.translate(horizontalPad.toFloat(), translationY)
            translationLayout.draw(canvas)
            canvas.restore()

            val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#7A6840")
                textSize = 26f * scale
                typeface = latinTypeface
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("$translatorName • Al-Quran", width / 2f, height - 105f * scale, footerPaint)

            val cardsDir = File(context.cacheDir, "shared_cards").apply { mkdirs() }
            val safeKey = verseKey.replace(":", "_")
            val file = File(cardsDir, "alquran_ayah_$safeKey.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
            return file
        } finally {
            bitmap.recycle()
        }
    }

    fun shareDuaAsImageCard(
        context: Context,
        title: String,
        arabicText: String,
        translationText: String,
        reference: String
    ) {
        val appContext = context.applicationContext
        shareScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    renderDuaCardFile(
                        context = appContext,
                        title = title,
                        arabicText = arabicText,
                        translationText = translationText,
                        reference = reference
                    )
                }
                val uri = FileProvider.getUriForFile(
                    appContext,
                    "${appContext.packageName}.fileprovider",
                    file
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, "$title\n\n$arabicText\n\n\"$translationText\"\n[Ref: $reference]")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(shareIntent, appContext.getString(R.string.share_as_card))
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                appContext.startActivity(chooser)
            } catch (e: Exception) {
                Toast.makeText(appContext, "Could not share Dua card: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderDuaCardFile(
        context: Context,
        title: String,
        arabicText: String,
        translationText: String,
        reference: String
    ): File {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val isLowRam = am?.isLowRamDevice == true
        val width = if (isLowRam) 720 else 1080
        val height = if (isLowRam) 900 else 1350
        val scale = width / 1080f

        val bitmap = Bitmap.createBitmap(
            width,
            height,
            if (isLowRam) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
        )
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.parseColor("#FAF7F0"))

            val bgOptions = BitmapFactory.Options().apply {
                inSampleSize = if (isLowRam) 3 else 2
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val ornament = BitmapFactory.decodeResource(context.resources, R.drawable.img_share_card_ornament, bgOptions)
            if (ornament != null) {
                val srcRect = Rect(0, 0, ornament.width, ornament.height)
                val dstRect = Rect(0, 0, width, height)
                val alphaPaint = Paint().apply { alpha = 52 }
                canvas.drawBitmap(ornament, srcRect, dstRect, alphaPaint)
                ornament.recycle()
            }

            val borderPaint = Paint().apply {
                color = Color.parseColor("#0F5C4D")
                style = Paint.Style.STROKE
                strokeWidth = 6f * scale
            }
            canvas.drawRect(44f * scale, 44f * scale, width - 44f * scale, height - 44f * scale, borderPaint)

            val goldPaint = Paint().apply {
                color = Color.parseColor("#C89D54")
                style = Paint.Style.STROKE
                strokeWidth = 3f * scale
            }
            canvas.drawRect(58f * scale, 58f * scale, width - 58f * scale, height - 58f * scale, goldPaint)

            val arabicTypeface = ResourcesCompat.getFont(context, R.font.amiri)
            val latinTypeface = ResourcesCompat.getFont(context, R.font.plus_jakarta_sans)

            val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F5C4D")
                textSize = 34f * scale
                typeface = latinTypeface
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }
            canvas.drawText(title, width / 2f, 150f * scale, headerPaint)

            val horizontalPad = (100f * scale).toInt()
            val textWidth = (width - horizontalPad * 2).coerceAtLeast(100)

            val arabicPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1E2421")
                textSize = 54f * scale
                typeface = arabicTypeface
            }
            val arabicLayout = StaticLayout.Builder
                .obtain(arabicText, 0, arabicText.length, arabicPaint, textWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(14f * scale, 1.25f)
                .build()

            val arabicY = 220f * scale
            canvas.save()
            canvas.translate(horizontalPad.toFloat(), arabicY)
            arabicLayout.draw(canvas)
            canvas.restore()

            val translationPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#35403B")
                textSize = 32f * scale
                typeface = latinTypeface
            }
            val translationY = (arabicY + arabicLayout.height + 60f * scale).coerceAtMost(height - 350f * scale)
            val translationLayout = StaticLayout.Builder
                .obtain(translationText, 0, translationText.length, translationPaint, textWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(10f * scale, 1.3f)
                .build()

            canvas.save()
            canvas.translate(horizontalPad.toFloat(), translationY)
            translationLayout.draw(canvas)
            canvas.restore()

            val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#7A6840")
                textSize = 26f * scale
                typeface = latinTypeface
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("Ref: $reference • Al-Quran", width / 2f, height - 105f * scale, footerPaint)

            val cardsDir = File(context.cacheDir, "shared_cards").apply { mkdirs() }
            val safeId = title.hashCode()
            val file = File(cardsDir, "alquran_dua_$safeId.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
            return file
        } finally {
            bitmap.recycle()
        }
    }
}

