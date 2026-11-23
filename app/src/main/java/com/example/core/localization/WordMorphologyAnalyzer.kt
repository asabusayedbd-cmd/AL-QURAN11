package com.example.core.localization

import com.example.core.database.WordEntity
import com.example.core.database.WordTranslationEntity

data class WordSegmentPart(
    val arabicPrefixOrPart: String,
    val roleLabel: String,
    val arabicRoleLabel: String,
    val banglaRoleLabel: String,
    val roleType: SegmentRoleType
)

enum class SegmentRoleType {
    PREFIX,
    STEM,
    SUFFIX
}

data class DetailedWordAnalysis(
    val partOfSpeechEn: String,
    val partOfSpeechAr: String,
    val partOfSpeechBn: String,
    val grammaticalCaseOrMood: String,
    val numberAndGender: String,
    val segments: List<WordSegmentPart>,
    val spacedRoot: String,
    val allMeanings: List<Pair<String, String>> // languageLabel to meaningText
)

object WordMorphologyAnalyzer {

    fun analyze(
        word: WordEntity,
        translations: List<WordTranslationEntity>
    ): DetailedWordAnalysis {
        val tag = word.grammarTagJson.trim()
        val lower = tag.lowercase()

        val (posEn, posAr, posBn) = when {
            lower.isBlank() -> Triple("Quranic Word (Kalimah)", "كلمة قرآنية", "কুরআনিক শব্দ")
            lower.contains("preposition") -> Triple("Preposition + Noun", "جار ومجرور / اسم", "অব্যয় ও বিশেষ্য")
            lower.contains("conjunction") && lower.contains("verb") -> Triple("Conjunction + Verb", "عطف وفعل", "যোজক ও ক্রিয়া")
            lower.contains("conjunction") && lower.contains("pronoun") -> Triple("Conjunction + Pronoun", "عطف وضمير", "যোজক ও সর্বনাম")
            lower.contains("proper noun") -> Triple("Proper Noun (Isim 'Alam)", "اسم علم", "নামবাচক বিশেষ্য")
            lower.contains("relative pronoun") -> Triple("Relative Pronoun", "اسم موصول", "সাপেক্ষ সর্বনাম")
            lower.contains("demonstrative") -> Triple("Demonstrative Pronoun", "اسم إشارة", "নির্দেশক সর্বনাম")
            lower.contains("pronoun") -> Triple("Personal Pronoun", "ضمير منفصل / متصل", "সর্বনাম")
            lower.contains("imperative") -> Triple("Imperative Verb (Amr)", "فعل أمر", "আদেশসূচক ক্রিয়া")
            lower.contains("imperfect") || lower.contains("present") -> Triple("Imperfect Verb (Mudari')", "فعل مضارع", "বর্তমান/ভবিষ্যৎ ক্রিয়া")
            lower.contains("perfect") || lower.contains("past") || lower.contains("verb") -> Triple("Verb (Fi'l)", "فعل", "ক্রিয়াপদ")
            lower.contains("active participle") -> Triple("Active Participle (Ism Fa'il)", "اسم فاعل", "কর্তৃবাচক বিশেষণ")
            lower.contains("passive participle") -> Triple("Passive Participle (Ism Maf'ul)", "اسم مفعول", "কর্মবাচক বিশেষণ")
            lower.contains("adjective") -> Triple("Adjective / Attribute (Sifah)", "صفة / نعت", "গুণবাচক বিশেষণ")
            lower.contains("negative") || lower.contains("negation") -> Triple("Particle of Negation", "حرف نفي", "না-বোধক অব্যয়")
            lower.contains("conditional") -> Triple("Conditional Particle", "أداة شرط", "শর্তবাচক অব্যয়")
            lower.contains("particle") -> Triple("Grammatical Particle (Harf)", "حرف", "অব্যয় পদ")
            else -> Triple("Noun / Substantive (Ism)", "اسم", "বিশেষ্য পদ")
        }

        val caseMood = when {
            lower.contains("genitive") -> "Genitive (مجرور / Majrur)"
            lower.contains("nominative") -> "Nominative (مرفوع / Marfu')"
            lower.contains("accusative") -> "Accusative (منصوب / Mansub)"
            lower.contains("jussive") -> "Jussive (مجزوم / Majzum)"
            lower.contains("subjunctive") -> "Subjunctive (منصوب / Mansub)"
            lower.contains("indicative") -> "Indicative (مرفوع / Marfu')"
            else -> "Contextual Inflection (إعراب السياق)"
        }

        val numGender = buildString {
            when {
                lower.contains("plural") -> append("Plural (جمع)")
                lower.contains("dual") -> append("Dual (مثنى)")
                else -> append("Singular (مفرد)")
            }
            append(" • ")
            when {
                lower.contains("feminine") -> append("Feminine (مؤنث)")
                else -> append("Masculine (مذكر)")
            }
        }

        val segments = deriveWordSegments(word.textUthmani, lower)
        val spacedRoot = word.rootArabic.trim()
        val orderedMeanings = translations
            .sortedBy {
                when (it.languageCode) {
                    "bn" -> 0
                    "en" -> 1
                    "ur" -> 2
                    "id" -> 3
                    "tr" -> 4
                    else -> 5
                }
            }
            .map { tr ->
                val langLabel = when (tr.languageCode.lowercase()) {
                    "bn" -> "বাংলা (Bangla)"
                    "en" -> "English"
                    "ur" -> "اردو (Urdu)"
                    "id" -> "Bahasa Indonesia"
                    "tr" -> "Türkçe"
                    "ar" -> "العربية"
                    "fr" -> "Français"
                    "es" -> "Español"
                    "de" -> "Deutsch"
                    "ru" -> "Русский"
                    "hi" -> "हिन्दी"
                    "ms" -> "Bahasa Melayu"
                    "fa" -> "فارسی"
                    "ta" -> "தமிழ்"
                    else -> tr.languageCode.uppercase()
                }
                langLabel to tr.meaningText
            }

        return DetailedWordAnalysis(
            partOfSpeechEn = posEn,
            partOfSpeechAr = posAr,
            partOfSpeechBn = posBn,
            grammaticalCaseOrMood = caseMood,
            numberAndGender = numGender,
            segments = segments,
            spacedRoot = spacedRoot,
            allMeanings = orderedMeanings
        )
    }

    private fun deriveWordSegments(arabic: String, lowerGrammar: String): List<WordSegmentPart> {
        val parts = mutableListOf<WordSegmentPart>()
        var stem = arabic.trim()

        // Common attached Arabic proclitics (diacritic-safe)
        if (lowerGrammar.contains("preposition") && stem.startsWith("بِ")) {
            parts.add(WordSegmentPart("بِـ", "Prefix: Preposition (with/in)", "حرف جر: الباء", "উপসর্গ: بِـ (দ্বারা/নামে)", SegmentRoleType.PREFIX))
            stem = stem.removePrefix("بِ")
        } else if (lowerGrammar.contains("preposition") && stem.startsWith("لِ")) {
            parts.add(WordSegmentPart("لِـ", "Prefix: Preposition (for/to)", "حرف جر: اللام", "উপসর্গ: لِـ (জন্য)", SegmentRoleType.PREFIX))
            stem = stem.removePrefix("لِ")
        } else if (lowerGrammar.contains("conjunction") && (stem.startsWith("و") || stem.startsWith("ف"))) {
            var cut = 1
            while (cut < stem.length && stem[cut] in '\u064B'..'\u065F') {
                cut++
            }
            val conj = stem.substring(0, cut)
            parts.add(WordSegmentPart("${conj}ـ", "Prefix: Conjunction (and/then)", "حرف عطف", "উপসর্গ: সংযোজক", SegmentRoleType.PREFIX))
            stem = stem.substring(cut)
        }

        if (stem.startsWith("ٱلْ") || stem.startsWith("ٱل") || stem.startsWith("الْ") || stem.startsWith("ال")) {
            val alPrefix = when {
                stem.startsWith("ٱلْ") -> "ٱلْ"
                stem.startsWith("ٱل") -> "ٱل"
                stem.startsWith("الْ") -> "الْ"
                else -> "ال"
            }
            parts.add(WordSegmentPart("${alPrefix}ـ", "Prefix: Definite Article (Al-)", "أداة تعريف (ال)", "নির্দিষ্টতাজ্ঞাপক (আল্)", SegmentRoleType.PREFIX))
            stem = stem.removePrefix(alPrefix)
        }

        parts.add(
            WordSegmentPart(
                arabicPrefixOrPart = stem.ifBlank { arabic },
                roleLabel = "Core Word Stem",
                arabicRoleLabel = "أصل الكلمة",
                banglaRoleLabel = "মূল শব্দ",
                roleType = SegmentRoleType.STEM
            )
        )

        // Common attached object/possessive pronouns
        if (lowerGrammar.contains("attached pronoun") || lowerGrammar.contains("possessive")) {
            val suffixLabel = when {
                stem.endsWith("نَا") -> "ـنَا" to "Pronoun Suffix: us / our (1st Pl.)"
                stem.endsWith("كُمْ") -> "ـكُمْ" to "Pronoun Suffix: you / your (2nd Pl.)"
                stem.endsWith("كَ") -> "ـكَ" to "Pronoun Suffix: you / your (2nd Masc. Sg.)"
                stem.endsWith("هُمْ") -> "ـهُمْ" to "Pronoun Suffix: them / their (3rd Pl.)"
                stem.endsWith("هُ") || stem.endsWith("هِ") -> "ـهُ / ـهِ" to "Pronoun Suffix: him / it (3rd Masc. Sg.)"
                else -> "ـضَمِير" to "Attached Pronoun Suffix"
            }
            parts.add(WordSegmentPart(suffixLabel.first, suffixLabel.second, "ضمير متصل", "যুক্ত সর্বনাম", SegmentRoleType.SUFFIX))
        }

        return parts
    }
}
