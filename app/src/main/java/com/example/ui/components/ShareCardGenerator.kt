package com.example.ui.components

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
import java.io.File
import java.io.FileOutputStream

object ShareCardGenerator {

    fun shareAyahAsImageCard(
        context: Context,
        surahName: String,
        verseKey: String,
        arabicText: String,
        translationText: String,
        translatorName: String
    ) {
        try {
            val width = 1080
            val height = 1350
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Subtle cream base
            canvas.drawColor(Color.parseColor("#FAF7F0"))

            // Draw pre-bundled calligraphic ornament frame
            val bgOptions = BitmapFactory.Options().apply { inSampleSize = 1 }
            val ornament = BitmapFactory.decodeResource(context.resources, R.drawable.img_share_card_ornament, bgOptions)
            if (ornament != null) {
                val srcRect = Rect(0, 0, ornament.width, ornament.height)
                val dstRect = Rect(0, 0, width, height)
                val alphaPaint = Paint().apply { alpha = 55 }
                canvas.drawBitmap(ornament, srcRect, dstRect, alphaPaint)
                ornament.recycle()
            }

            // Emerald border frame
            val borderPaint = Paint().apply {
                color = Color.parseColor("#0F5C4D")
                style = Paint.Style.STROKE
                strokeWidth = 6f
            }
            canvas.drawRect(44f, 44f, width - 44f, height - 44f, borderPaint)

            val goldPaint = Paint().apply {
                color = Color.parseColor("#C89D54")
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }
            canvas.drawRect(58f, 58f, width - 58f, height - 58f, goldPaint)

            val arabicTypeface = ResourcesCompat.getFont(context, R.font.amiri)
            val latinTypeface = ResourcesCompat.getFont(context, R.font.plus_jakarta_sans)

            val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F5C4D")
                textSize = 36f
                typeface = latinTypeface
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("$surahName • $verseKey", width / 2f, 150f, headerPaint)

            val arabicPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1E2421")
                textSize = 58f
                typeface = arabicTypeface
            }
            val arabicLayout = StaticLayout.Builder
                .obtain(arabicText, 0, arabicText.length, arabicPaint, width - 200)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(14f, 1.25f)
                .build()

            canvas.save()
            canvas.translate(100f, 240f)
            arabicLayout.draw(canvas)
            canvas.restore()

            val translationPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#35403B")
                textSize = 34f
                typeface = latinTypeface
            }
            val translationY = (240f + arabicLayout.height + 70f).coerceAtMost(height - 320f)
            val translationLayout = StaticLayout.Builder
                .obtain(translationText, 0, translationText.length, translationPaint, width - 200)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(10f, 1.3f)
                .build()

            canvas.save()
            canvas.translate(100f, translationY)
            translationLayout.draw(canvas)
            canvas.restore()

            val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#7A6840")
                textSize = 26f
                typeface = latinTypeface
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("$translatorName • Noor Al-Quran", width / 2f, height - 105f, footerPaint)

            val cardsDir = File(context.cacheDir, "shared_cards").apply { mkdirs() }
            val safeKey = verseKey.replace(":", "_")
            val file = File(cardsDir, "noor_ayah_$safeKey.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
            }
            bitmap.recycle()

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "$arabicText\n\n\"$translationText\" [$surahName $verseKey]")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, context.getString(R.string.share_as_card))
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not generate card: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
