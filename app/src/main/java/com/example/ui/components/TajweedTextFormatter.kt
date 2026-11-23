package com.example.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.example.ui.theme.TajweedPalette

object TajweedTextFormatter {

    private val TAJWEED_TAG_REGEX = Regex(
        "<tajweed\\s+class=[\"']?([a-zA-Z0-9_\\-]+)[\"']?[^>]*>(.*?)</tajweed>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val END_SPAN_REGEX = Regex("<span\\s+class=[\"']?end[\"']?[^>]*>.*?</span>", RegexOption.IGNORE_CASE)
    private val ANY_HTML_TAG_REGEX = Regex("<[^>]+>")

    fun formatArabicVerse(
        rawText: String,
        tajweedHtml: String,
        tajweedEnabled: Boolean,
        palette: TajweedPalette,
        ayahEndMarker: String = "",
        useTajweedMarkup: Boolean = true
    ): AnnotatedString {
        val cleanedRaw = END_SPAN_REGEX.replace(rawText, "").trim()
        val cleanedTajweedHtml = END_SPAN_REGEX.replace(tajweedHtml, "").trim()

        if (!tajweedEnabled || !useTajweedMarkup || !cleanedTajweedHtml.contains("<tajweed", ignoreCase = true)) {
            return buildAnnotatedString {
                append(cleanedRaw)
                if (ayahEndMarker.isNotEmpty()) {
                    append("  ")
                    pushStyle(SpanStyle(color = palette.madd))
                    append(ayahEndMarker)
                    pop()
                }
            }
        }

        return buildAnnotatedString {
            var lastIndex = 0
            val matches = TAJWEED_TAG_REGEX.findAll(cleanedTajweedHtml)
            for (match in matches) {
                val start = match.range.first
                if (start > lastIndex) {
                    val plain = ANY_HTML_TAG_REGEX.replace(cleanedTajweedHtml.substring(lastIndex, start), "")
                    append(decodeEntities(plain))
                }
                val ruleClass = match.groupValues[1].lowercase()
                val innerText = decodeEntities(ANY_HTML_TAG_REGEX.replace(match.groupValues[2], ""))
                val color = mapRuleToColor(ruleClass, palette)
                if (color != null) {
                    pushStyle(SpanStyle(color = color))
                    append(innerText)
                    pop()
                } else {
                    append(innerText)
                }
                lastIndex = match.range.last + 1
            }
            if (lastIndex < cleanedTajweedHtml.length) {
                val tail = ANY_HTML_TAG_REGEX.replace(cleanedTajweedHtml.substring(lastIndex), "")
                append(decodeEntities(tail))
            }
            if (ayahEndMarker.isNotEmpty()) {
                append("  ")
                pushStyle(SpanStyle(color = palette.ghunnah))
                append(ayahEndMarker)
                pop()
            }
        }
    }

    private fun decodeEntities(input: String): String {
        return input
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
    }

    private fun mapRuleToColor(ruleClass: String, palette: TajweedPalette): Color? = when {
        ruleClass.contains("ghunnah") -> palette.ghunnah
        ruleClass.contains("ikhfa") -> palette.ikhfa
        ruleClass.contains("idgham") -> palette.idgham
        ruleClass.contains("qalqalah") -> palette.qalqalah
        ruleClass.contains("madda") || ruleClass.contains("madd") -> palette.madd
        ruleClass.contains("iqlab") -> palette.iqlab
        ruleClass.contains("ham_wasl") || ruleClass.contains("slnt") || ruleClass.contains("laam_shamsiyah") -> palette.silent
        else -> null
    }
}
