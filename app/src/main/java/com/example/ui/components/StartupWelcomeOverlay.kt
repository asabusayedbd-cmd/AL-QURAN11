package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.localization.ArabicFontFamilyOption
import com.example.core.localization.QuranScriptType
import com.example.core.perf.PerfTracker
import com.example.ui.theme.arabicScriptFont
import com.example.ui.theme.banglaSafeBodyStyle
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

enum class InspirationSourceKind { QURAN_AYAH, SAHIH_HADITH }

data class StartupInspiration(
    val kind: InspirationSourceKind,
    val arabicText: String,
    val textEn: String,
    val textBn: String,
    val referenceEn: String,
    val referenceBn: String
)

object StartupInspirationCatalog {
    val items: List<StartupInspiration> = listOf(
        StartupInspiration(
            kind = InspirationSourceKind.SAHIH_HADITH,
            arabicText = "خَيْرُكُمْ مَنْ تَعَلَّمَ الْقُرْآنَ وَعَلَّمَهُ",
            textEn = "The best among you are those who learn the Quran and teach it.",
            textBn = "তোমাদের মধ্যে সর্বোত্তম সেই ব্যক্তি, যে নিজে কুরআন শেখে এবং অন্যকে শেখায়।",
            referenceEn = "Sahih al-Bukhari 5027",
            referenceBn = "সহীহ বুখারী ৫০২৭"
        ),
        StartupInspiration(
            kind = InspirationSourceKind.QURAN_AYAH,
            arabicText = "فَإِنَّ مَعَ الْعُسْرِ يُسْرًا ۝ إِنَّ مَعَ الْعُسْرِ يُسْرًا",
            textEn = "For indeed, with hardship comes ease. Indeed, with hardship comes ease.",
            textBn = "নিশ্চয়ই কষ্টের সাথেই রয়েছে স্বস্তি। নিশ্চয়ই কষ্টের সাথেই রয়েছে স্বস্তি।",
            referenceEn = "Surah Ash-Sharh 94:5–6",
            referenceBn = "সূরা আল-ইনশিরাহ ৯৪:৫–৬"
        ),
        StartupInspiration(
            kind = InspirationSourceKind.SAHIH_HADITH,
            arabicText = "اقْرَءُوا الْقُرْآنَ فَإِنَّهُ يَأْتِي يَوْمَ الْقِيَامَةِ شَفِيعًا لِأَصْحَابِهِ",
            textEn = "Recite the Quran, for on the Day of Resurrection it will come as an intercessor for its companions.",
            textBn = "তোমরা কুরআন তিলাওয়াত করো; কেননা কিয়ামতের দিন এটি তার পাঠকদের জন্য সুপারিশকারী হয়ে আসবে।",
            referenceEn = "Sahih Muslim 804a",
            referenceBn = "সহীহ মুসলিম ৮০৪"
        ),
        StartupInspiration(
            kind = InspirationSourceKind.QURAN_AYAH,
            arabicText = "أَلَا بِذِكْرِ اللَّهِ تَطْمَئِنُّ الْقُلُوبُ",
            textEn = "Unquestionably, by the remembrance of Allah do hearts find tranquility.",
            textBn = "জেনে রাখো, আল্লাহর স্মরণেই অন্তরসমূহ প্রশান্তি লাভ করে।",
            referenceEn = "Surah Ar-Ra'd 13:28",
            referenceBn = "সূরা আর-রা‘দ ১৩:২৮"
        ),
        StartupInspiration(
            kind = InspirationSourceKind.SAHIH_HADITH,
            arabicText = "مَنْ قَرَأَ حَرْفًا مِنْ كِتَابِ اللَّهِ فَلَهُ بِهِ حَسَنَةٌ، وَالْحَسَنَةُ بِعَشْرِ أَمْثَالِهَا",
            textEn = "Whoever recites a single letter from the Book of Allah receives a good deed, and every good deed is multiplied tenfold.",
            textBn = "যে ব্যক্তি আল্লাহর কিতাব থেকে একটি হরফ তিলাওয়াত করবে, সে এর বিনিময়ে একটি নেকি পাবে; আর প্রতিটি নেকি দশগুণ করে বাড়ানো হয়।",
            referenceEn = "Jami' at-Tirmidhi 2910 (Sahih)",
            referenceBn = "তিরমিযী ২৯১০ (সহীহ)"
        ),
        StartupInspiration(
            kind = InspirationSourceKind.QURAN_AYAH,
            arabicText = "لَا يُكَلِّفُ اللَّهُ نَفْسًا إِلَّا وُسْعَهَا",
            textEn = "Allah does not burden a soul beyond that it can bear.",
            textBn = "আল্লাহ কোনো প্রাণকে তার সাধ্যের অতিরিক্ত দায়িত্ব অর্পণ করেন না।",
            referenceEn = "Surah Al-Baqarah 2:286",
            referenceBn = "সূরা আল-বাকারা ২:২৮৬"
        ),
        StartupInspiration(
            kind = InspirationSourceKind.SAHIH_HADITH,
            arabicText = "مَنْ سَلَكَ طَرِيقًا يَلْتَمِسُ فِيهِ عِلْمًا، سَهَّلَ اللَّهُ لَهُ بِهِ طَرِيقًا إِلَى الْجَنَّةِ",
            textEn = "Whoever treads a path seeking knowledge, Allah makes easy for him a path to Paradise.",
            textBn = "যে ব্যক্তি জ্ঞান অন্বেষণের পথে চলে, আল্লাহ তার জন্য জান্নাতের পথ সহজ করে দেন।",
            referenceEn = "Sahih Muslim 2699a",
            referenceBn = "সহীহ মুসলিম ২৬৯৯"
        )
    )

    fun pickForLaunch(seedMillis: Long = System.currentTimeMillis()): StartupInspiration {
        val index = ((seedMillis / 1000L).let { kotlin.math.abs(it) } % items.size).toInt()
        return items[index]
    }
}

@Composable
fun StartupWelcomeOverlay(
    visible: Boolean,
    uiLanguageCode: String,
    inspiration: StartupInspiration = remember { StartupInspirationCatalog.pickForLaunch() },
    autoDismissDelayMs: Long = 2200L,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(visible) {
        if (visible) {
            PerfTracker.markStartupWelcomeShown()
            if (autoDismissDelayMs > 0L) {
                delay(autoDismissDelayMs)
                onDismiss()
            }
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = EnterTransition.None,
        exit = fadeOut(tween(200)),
        modifier = modifier.fillMaxSize()
    ) {
        val isBangla = uiLanguageCode.equals("bn", ignoreCase = true)
        val goldAccent = Color(0xFFF3CF82)
        val deepEmeraldTop = Color(0xFF06251F)
        val deepEmeraldMid = Color(0xFF0B3B31)
        val deepEmeraldBottom = Color(0xFF041C17)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(deepEmeraldTop, deepEmeraldMid, deepEmeraldBottom)
                    )
                )
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .testTag("startup_welcome_overlay"),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )

            // Subtle sacred geometric octagram watermark behind emblem
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height * 0.28f)
                val baseRadius = size.minDimension * 0.24f
                drawCircle(
                    color = goldAccent.copy(alpha = 0.08f),
                    radius = baseRadius * 1.35f,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )
                drawCircle(
                    color = goldAccent.copy(alpha = 0.12f),
                    radius = baseRadius,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
                val points = 12
                for (i in 0 until points) {
                    val angle = Math.toRadians((i * (360.0 / points)))
                    val start = Offset(
                        x = center.x + (baseRadius * 0.9f * cos(angle)).toFloat(),
                        y = center.y + (baseRadius * 0.9f * sin(angle)).toFloat()
                    )
                    val end = Offset(
                        x = center.x + (baseRadius * 1.45f * cos(angle)).toFloat(),
                        y = center.y + (baseRadius * 1.45f * sin(angle)).toFloat()
                    )
                    drawLine(
                        color = goldAccent.copy(alpha = 0.09f),
                        start = start,
                        end = end,
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }

            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // App Logo & Title
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF0E4839),
                        border = BorderStroke(2.dp, goldAccent.copy(alpha = 0.85f)),
                        shadowElevation = 10.dp,
                        modifier = Modifier
                            .size(80.dp)
                            .testTag("startup_app_logo")
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.quran_calligraphy_icon),
                            contentDescription = stringResource(R.string.app_name),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                    }

                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = goldAccent
                    )
                    Text(
                        text = stringResource(R.string.startup_subtitle),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFFDCECE7),
                        textAlign = TextAlign.Center
                    )
                }

                // Motivating Ayah / Hadith Card with Verified Reference
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0x33041A15),
                    border = BorderStroke(1.dp, goldAccent.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("startup_inspiration_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = goldAccent.copy(alpha = 0.16f),
                            border = BorderStroke(0.8.dp, goldAccent.copy(alpha = 0.45f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (inspiration.kind == InspirationSourceKind.QURAN_AYAH) {
                                        Icons.AutoMirrored.Filled.MenuBook
                                    } else {
                                        Icons.Default.AutoAwesome
                                    },
                                    contentDescription = null,
                                    tint = goldAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = stringResource(
                                        if (inspiration.kind == InspirationSourceKind.QURAN_AYAH) {
                                            R.string.startup_badge_quran
                                        } else {
                                            R.string.startup_badge_hadith
                                        }
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = goldAccent
                                )
                            }
                        }

                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                            Text(
                                text = inspiration.arabicText,
                                fontFamily = arabicScriptFont(
                                    QuranScriptType.UTHMANI,
                                    ArabicFontFamilyOption.AMIRI
                                ),
                                fontSize = 23.sp,
                                lineHeight = 40.sp,
                                textAlign = TextAlign.Center,
                                color = Color(0xFFFAF7F0),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        val translatedBody = if (isBangla) inspiration.textBn else inspiration.textEn
                        val baseStyle = banglaSafeBodyStyle(15.5f, if (isBangla) "bn" else "en")
                        Text(
                            text = "“$translatedBody”",
                            style = baseStyle.copy(
                                color = Color(0xFFE7F2EE),
                                textAlign = TextAlign.Center
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0x400E4839),
                            modifier = Modifier.testTag("startup_inspiration_reference")
                        ) {
                            Text(
                                text = if (isBangla) inspiration.referenceBn else inspiration.referenceEn,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = goldAccent,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = goldAccent,
                        contentColor = Color(0xFF06251F)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .height(52.dp)
                        .fillMaxWidth(0.72f)
                        .testTag("startup_continue_button")
                ) {
                    Text(
                        text = stringResource(R.string.startup_enter_app),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
