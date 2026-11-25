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
    val grammaticalCaseBn: String,
    val numberAndGender: String,
    val numberAndGenderBn: String,
    val segments: List<WordSegmentPart>,
    val spacedRoot: String,
    val stemArabic: String,
    val explanationEn: String,
    val explanationBn: String,
    val explanationAr: String,
    val allMeanings: List<Pair<String, String>>, // languageLabel to meaningText
    val normalizedStem: String = "",
    val sarfPatternEn: String = "",
    val sarfPatternAr: String = "",
    val sarfPatternBn: String = "",
    val sarfNoteEn: String = "",
    val sarfNoteBn: String = "",
    val nahwFunctionEn: String = "",
    val nahwFunctionBn: String = "",
    val definitenessEn: String = "",
    val definitenessBn: String = ""
) {
    fun localizedPos(uiLang: String): String = when (uiLang.lowercase()) {
        "bn" -> "$partOfSpeechBn ($partOfSpeechAr)"
        "ar" -> partOfSpeechAr
        else -> "$partOfSpeechEn • $partOfSpeechAr"
    }

    fun localizedCaseMood(uiLang: String): String = when (uiLang.lowercase()) {
        "bn" -> grammaticalCaseBn
        else -> grammaticalCaseOrMood
    }

    fun localizedNumberGender(uiLang: String): String = when (uiLang.lowercase()) {
        "bn" -> numberAndGenderBn
        else -> numberAndGender
    }

    fun localizedSarfPattern(uiLang: String): String = when (uiLang.lowercase()) {
        "bn" -> "$sarfPatternBn ($sarfPatternAr)"
        "ar" -> sarfPatternAr
        else -> "$sarfPatternEn • $sarfPatternAr"
    }

    fun localizedNahwFunction(uiLang: String): String = when (uiLang.lowercase()) {
        "bn" -> nahwFunctionBn
        else -> nahwFunctionEn
    }

    fun localizedDefiniteness(uiLang: String): String = when (uiLang.lowercase()) {
        "bn" -> definitenessBn
        else -> definitenessEn
    }

    fun localizedExplanation(uiLang: String): String = when (uiLang.lowercase()) {
        "bn" -> explanationBn
        "ar" -> explanationAr
        else -> explanationEn
    }
}

object WordMorphologyAnalyzer {

    private val DIACRITIC_REGEX = Regex("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]")
    private val WAQF_MARKS_REGEX = Regex("[\\u06D6-\\u06DC\\u06DF-\\u06E8\\u06EA-\\u06ED]+$")

    private data class KnownLexiconEntry(
        val posEn: String,
        val posAr: String,
        val posBn: String,
        val caseEn: String,
        val caseBn: String,
        val numGenderEn: String,
        val numGenderBn: String
    )

    private val KNOWN_FUNCTION_WORDS: Map<String, KnownLexiconEntry> = mapOf(
        "في" to KnownLexiconEntry("Preposition (Harf Jarr)", "حرف جر", "সম্বন্ধসূচক অব্যয় (মাজরুরকারক)", "Governs Genitive (مجرور)", "পরবর্তী শব্দে জের দেয় (মাজরুর)", "Particle • Invariant", "অব্যয় • অপরিবর্তনীয়"),
        "من" to KnownLexiconEntry("Preposition / Relative Pronoun", "حرف جر / اسم موصول", "অব্যয় / সাপেক্ষ সর্বনাম (হতে/যে)", "Governs Genitive (مجرور)", "হতে/থেকে অর্থে মাজরুরকারক", "Particle • Invariant", "অব্যয় • অপরিবর্তনীয়"),
        "على" to KnownLexiconEntry("Preposition (Harf Jarr)", "حرف جر", "অব্যয় (উপর/প্রতি)", "Governs Genitive (مجرور)", "উপর অর্থে মাজরুরকারক", "Particle • Invariant", "অব্যয় • অপরিবর্তনীয়"),
        "إلى" to KnownLexiconEntry("Preposition (Harf Jarr)", "حرف جر", "অব্যয় (দিকে/প্রতি)", "Governs Genitive (مجرور)", "দিকে অর্থে মাজরুরকারক", "Particle • Invariant", "অব্যয় • অপরিবর্তনীয়"),
        "عن" to KnownLexiconEntry("Preposition (Harf Jarr)", "حرف جر", "অব্যয় (সম্পর্কে/হতে)", "Governs Genitive (مجرور)", "সম্পর্কে/হতে মাজরুরকারক", "Particle • Invariant", "অব্যয় • অপরিবর্তনীয়"),
        "إن" to KnownLexiconEntry("Emphatic Particle (Harf Mushabbah)", "حرف توكيد ونصب", "নিশ্চয়তাবোধক অব্যয় (নিশ্চয়ই)", "Accusative Subject (منصوب)", "বিশেষ্যকে যবর (মানসুব) করে", "Particle • Emphasis", "অব্যয় • জোরালো অর্থ"),
        "أن" to KnownLexiconEntry("Subjunctive / Emphatic Particle", "حرف مصدري ونصب", "সংযোজক/নিশ্চয়তাবোধক অব্যয় (যে)", "Subjunctive / Accusative", "মানসুবকারক অব্যয়", "Particle • Invariant", "অব্যয় • অপরিবর্তনীয়"),
        "لا" to KnownLexiconEntry("Particle of Negation / Prohibition", "حرف نفي / نهي", "না-বোধক বা নিষেধসূচক অব্যয়", "Jussive / Negation", "না-বোধক / নিষেধ", "Particle • Negation", "অব্যয় • না-বোধক"),
        "ما" to KnownLexiconEntry("Negation / Relative Pronoun", "حرف نفي / اسم موصول", "না-বোধক অব্যয় / যা কিছু", "Contextual Inflection", "প্রসঙ্গভিত্তিক অবস্থা", "Particle / Pronoun", "অব্যয় / সর্বনাম"),
        "لم" to KnownLexiconEntry("Negative Jussive Particle", "حرف نفي وجزم", "অতীত না-বোধক জযমকারক অব্যয়", "Jussive (مجزوم)", "ক্রিয়াকে জযম (মাজযুম) করে", "Particle • Past Negation", "অব্যয় • অতীত নাকচ"),
        "لن" to KnownLexiconEntry("Negative Subjunctive Particle", "حرف نفي ونصب", "ভবিষ্যৎ না-বোধক অব্যয় (কখনো না)", "Subjunctive (منصوب)", "ভবিষ্যৎ ক্রিয়াকে যবর দেয়", "Particle • Future Negation", "অব্যয় • ভবিষ্যৎ নাকচ"),
        "قد" to KnownLexiconEntry("Particle of Certainty", "حرف تحقيق", "নিশ্চয়তা/সম্পন্নতা সূচক অব্যয়", "Invariant (مبني)", "অপরিবর্তনীয় অব্যয়", "Particle • Invariant", "অব্যয় • নিশ্চয়তা"),
        "ثم" to KnownLexiconEntry("Conjunction (Then / Thereafter)", "حرف عطف", "সংযোজক অব্যয় (অতঃপর)", "Invariant (مبني)", "সংযোজক অব্যয়", "Particle • Conjunction", "অব্যয় • সংযোজক"),
        "أو" to KnownLexiconEntry("Conjunction (Or)", "حرف عطف", "বিকল্প সংযোজক অব্যয় (অথবা)", "Invariant (مبني)", "বিকল্প অব্যয়", "Particle • Conjunction", "অব্যয় • সংযোজক"),
        "بل" to KnownLexiconEntry("Retraction Particle (Rather)", "حرف إضراب", "সংশোধন অব্যয় (বরং)", "Invariant (مبني)", "অপরিবর্তনীয় অব্যয়", "Particle • Invariant", "অব্যয়"),
        "حتى" to KnownLexiconEntry("Preposition / Limit (Until)", "حرف غاية وجر", "সীমা নির্দেশক অব্যয় (যতক্ষণ না)", "Governs Genitive / Subjunctive", "সীমা নির্দেশক", "Particle • Invariant", "অব্যয়"),
        "إذا" to KnownLexiconEntry("Conditional Time Particle (When)", "ظرف لما يستقبل من الزمان", "শর্তবাচক সময় অব্যয় (যখন)", "Conditional Adverb", "সময় ও শর্তসূচক", "Particle • Conditional", "অব্যয় • শর্ত"),
        "إذ" to KnownLexiconEntry("Time Particle (When / Recall)", "ظرف للماضي", "অতীত সময়সূচক অব্যয় (যখন)", "Adverb of Past Time", "অতীত সময় নির্দেশক", "Particle • Time", "অব্যয় • সময়"),
        "هو" to KnownLexiconEntry("Personal Pronoun (He / It)", "ضمير منفصل", "ব্যক্তিবাচক সর্বনাম (তিনি/সে)", "Nominative (في محل رفع)", "কর্তা কারক (মারফু)", "Singular • Masculine", "একবচন • পুংলিঙ্গ"),
        "هي" to KnownLexiconEntry("Personal Pronoun (She / It)", "ضمير منفصل", "ব্যক্তিবাচক সর্বনাম (সে/ইহা)", "Nominative (في محل رفع)", "কর্তা কারক (মারফু)", "Singular • Feminine", "একবচন • স্ত্রীলিঙ্গ"),
        "هم" to KnownLexiconEntry("Personal Pronoun (They)", "ضمير منفصل", "ব্যক্তিবাচক সর্বনাম (তারা)", "Nominative (في محل رفع)", "কর্তা কারক (মারফু)", "Plural • Masculine", "বহুবচন • পুংলিঙ্গ"),
        "نحن" to KnownLexiconEntry("Personal Pronoun (We)", "ضمير منفصل", "উত্তম পুরুষ সর্বনাম (আমরা)", "Nominative (في محل رفع)", "কর্তা কারক (মারফু)", "Plural • 1st Person", "বহুবচন • উত্তম পুরুষ"),
        "أنت" to KnownLexiconEntry("Personal Pronoun (You)", "ضمير منفصل", "মধ্যম পুরুষ সর্বনাম (তুমি)", "Nominative (في محل رفع)", "কর্তা কারক (মারফু)", "Singular • Masculine", "একবচন • পুংলিঙ্গ"),
        "أنتم" to KnownLexiconEntry("Personal Pronoun (You all)", "ضمير منفصل", "মধ্যম পুরুষ সর্বনাম (তোমরা)", "Nominative (في محل رفع)", "কর্তা কারক (মারফু)", "Plural • Masculine", "বহুবচন • পুংলিঙ্গ"),
        "الذي" to KnownLexiconEntry("Relative Pronoun (Who / Which)", "اسم موصول", "সাপেক্ষ সর্বনাম (যিনি/যে)", "Contextual Inflection", "সাপেক্ষ সর্বনাম", "Singular • Masculine", "একবচন • পুংলিঙ্গ"),
        "الذين" to KnownLexiconEntry("Relative Pronoun (Those who)", "اسم موصول", "সাপেক্ষ সর্বনাম (যারা)", "Contextual Inflection", "সাপেক্ষ সর্বনাম", "Plural • Masculine", "বহুবচন • পুংলিঙ্গ"),
        "التي" to KnownLexiconEntry("Relative Pronoun (Who / Which)", "اسم موصول", "সাপেক্ষ সর্বনাম (যিনি/যা)", "Contextual Inflection", "সাপেক্ষ সর্বনাম", "Singular • Feminine", "একবচন • স্ত্রীলিঙ্গ"),
        "ذلك" to KnownLexiconEntry("Demonstrative Pronoun (That)", "اسم إشارة", "নির্দেশক সর্বনাম (উহা/সেটি)", "Contextual Inflection", "দূরবর্তী নির্দেশক", "Singular • Masculine", "একবচন • পুংলিঙ্গ"),
        "تلك" to KnownLexiconEntry("Demonstrative Pronoun (That)", "اسم إشارة", "নির্দেশক সর্বনাম (উহা/সেগুলো)", "Contextual Inflection", "দূরবর্তী নির্দেশক", "Singular • Feminine", "একবচন • স্ত্রীলিঙ্গ"),
        "هذا" to KnownLexiconEntry("Demonstrative Pronoun (This)", "اسم إشارة", "নির্দেশক সর্বনাম (ইহা/এই)", "Contextual Inflection", "নিকটবর্তী নির্দেশক", "Singular • Masculine", "একবচন • পুংলিঙ্গ"),
        "هٰذا" to KnownLexiconEntry("Demonstrative Pronoun (This)", "اسم إشارة", "নির্দেশক সর্বনাম (ইহা/এই)", "Contextual Inflection", "নিকটবর্তী নির্দেশক", "Singular • Masculine", "একবচন • পুংলিঙ্গ"),
        "أولٰئك" to KnownLexiconEntry("Demonstrative Pronoun (Those)", "اسم إشارة", "নির্দেশক সর্বনাম (তারা/ঐ সকল)", "Contextual Inflection", "বহুবচন নির্দেশক", "Plural • Masculine", "বহুবচন • পুংলিঙ্গ"),
        "أولئك" to KnownLexiconEntry("Demonstrative Pronoun (Those)", "اسم إشارة", "নির্দেশক সর্বনাম (তারা/ঐ সকল)", "Contextual Inflection", "বহুবচন নির্দেশক", "Plural • Masculine", "বহুবচন • পুংলিঙ্গ"),
        "الله" to KnownLexiconEntry("Proper Noun (Lafz al-Jalalah)", "لفظ الجلالة (اسم علم)", "সর্বশ্রেষ্ঠ নামবাচক বিশেষ্য (আল্লাহ্)", "Contextual Inflection", "প্রসঙ্গভিত্তিক ই‘রাব", "Singular • Unique Name", "একক নামবাচক"),
        "لله" to KnownLexiconEntry("Preposition + Proper Noun", "جار ومجرور (لله)", "অব্যয় ও নামবাচক বিশেষ্য (আল্লাহর জন্য)", "Genitive (مجرور)", "সম্বন্ধ কারক / মাজরুর (জেরযুক্ত)", "Singular • Unique Name", "একক নামবাচক"),
        "قل" to KnownLexiconEntry("Imperative Verb (Amr)", "فعل أمر", "আদেশসূচক ক্রিয়া (বলুন)", "Jussive / Built on Sukun (مبني على السكون)", "সুকুনযুক্ত আদেশসূচক ক্রিয়া", "Singular • 2nd Person Masc.", "একবচন • মধ্যম পুরুষ"),
        "قال" to KnownLexiconEntry("Perfect Verb (Past)", "فعل ماضٍ", "অতীত কালের ক্রিয়া (তিনি বললেন)", "Built on Fathah (مبني على الفتح)", "ফাতহাযুক্ত অতীত ক্রিয়া", "Singular • 3rd Person Masc.", "একবচন • পুংলিঙ্গ"),
        "قالوا" to KnownLexiconEntry("Perfect Verb (Past Plural)", "فعل ماضٍ + واو الجماعة", "অতীত কালের বহুবচন ক্রিয়া (তারা বলল)", "Built on Dammah (مبني على الضم)", "বহুবচন অতীত ক্রিয়া", "Plural • 3rd Person Masc.", "বহুবচন • পুংলিঙ্গ"),
        "كان" to KnownLexiconEntry("Auxiliary Past Verb (Fi'l Naqis)", "فعل ماضٍ ناقص", "অসম্পূর্ণ অতীত ক্রিয়া (ছিলেন/ছিল)", "Built on Fathah (مبني على الفتح)", "অসম্পূর্ণ অতীত ক্রিয়া", "Singular • 3rd Person Masc.", "একবচন • পুংলিঙ্গ"),
        "يا" to KnownLexiconEntry("Vocative Particle (O!)", "حرف نداء", "সম্বোধনসূচক অব্যয় (হে!)", "Vocative (نداء)", "সম্বোধন অব্যয়", "Particle • Vocative", "অব্যয় • সম্বোধন"),
        "رب" to KnownLexiconEntry("Noun (Lord / Sustainer)", "اسم (رب)", "বিশেষ্য (রব / প্রতিপালক)", "Contextual Inflection", "বাক্যগত অবস্থা", "Singular • Masculine", "একবচন • পুংলিঙ্গ"),
        "يوم" to KnownLexiconEntry("Noun / Adverb of Time (Day)", "اسم / ظرف زمان", "বিশেষ্য / সময়বাচক পদ (দিন)", "Contextual Inflection", "বাক্যগত অবস্থা", "Singular • Masculine", "একবচন • পুংলিঙ্গ"),
        "كل" to KnownLexiconEntry("Quantifier Noun (Every / All)", "اسم يفيد الشمول (كل)", "সামষ্টিক বিশেষ্য (প্রত্যেক/সব)", "Construct State (مضاف)", "সম্বন্ধ পদের পূর্বপদ (মুদাফ)", "Singular Form • Collective", "একক রূপ • সামষ্টিক"),
        "عند" to KnownLexiconEntry("Adverb of Place/Time (With / Near)", "ظرف مكان / زمان", "স্থান/কালবাচক অব্যয় (নিকট)", "Accusative Adverb (منصوب)", "স্থান/কাল মানসুব", "Adverb • Construct", "ক্রিয়াবিশেষণ"),
        "مع" to KnownLexiconEntry("Adverbial Noun (With / Together)", "ظرف يفيد المصاحبة", "সহযোগিতাবাচক পদ (সাথে)", "Accusative / Construct", "মানসুব / মুদাফ", "Invariant Form", "অপরিবর্তনীয় রূপ"),
        "بين" to KnownLexiconEntry("Adverb of Place (Between)", "ظرف مكان", "স্থানবাচক পদ (মাঝে/মধ্যে)", "Accusative Adverb (منصوب)", "স্থানবাচক মানসুব", "Adverb • Construct", "স্থানবাচক"),
        "قبل" to KnownLexiconEntry("Adverb of Time (Before)", "ظرف زمان", "সময়বাচক পদ (পূর্বে)", "Accusative / Genitive", "মানসুব / মাজরুর", "Adverb • Time", "সময়বাচক"),
        "بعد" to KnownLexiconEntry("Adverb of Time (After)", "ظرف زمان", "সময়বাচক পদ (পরে)", "Accusative / Genitive", "মানসুব / মাজরুর", "Adverb • Time", "সময়বাচক"),
        "إلا" to KnownLexiconEntry("Exception Particle (Except)", "أداة استثناء", "ব্যতিক্রমসূচক অব্যয় (ব্যতীত)", "Exception Government", "ইস্তিসনা (ব্যতিক্রম)", "Particle • Invariant", "অব্যয় • ব্যতিক্রম"),
        "إنما" to KnownLexiconEntry("Restrictive Particle (Only / Indeed)", "أداة حصر", "সীমাবদ্ধতাসূচক অব্যয় (কেবল/নিশ্চয়ই)", "Invariant (مبني)", "অপরিবর্তনীয় অব্যয়", "Particle • Restriction", "অব্যয় • সীমাবদ্ধতা"),
        "لعل" to KnownLexiconEntry("Particle of Hope/Expectation", "حرف ترجٍّ ونصب", "আশাবাচক অব্যয় (যাতে/সম্ভবত)", "Accusative Subject (منصوب)", "মানসুবকারক অব্যয়", "Particle • Invariant", "অব্যয় • আশা"),
        "لو" to KnownLexiconEntry("Conditional Particle (If)", "حرف شرط غير جازم", "শর্তবাচক অব্যয় (যদি)", "Non-Jussive Conditional", "অব্যয় • শর্ত", "Particle • Conditional", "অব্যয় • শর্ত"),
        "لولا" to KnownLexiconEntry("Conditional Particle (Were it not)", "حرف امتناع لوجود", "শর্তবাচক অব্যয় (যদি না হতো)", "Nominal Clause Marker", "শর্তবাচক অব্যয়", "Particle • Conditional", "অব্যয় • শর্ত"),
        "كيف" to KnownLexiconEntry("Interrogative Adverb (How)", "اسم استفهام", "প্রশ্নবোধক পদ (কীভাবে)", "Built on Fathah (مبني)", "প্রশ্নবোধক • অপরিবর্তনীয়", "Interrogative", "প্রশ্নবোধক"),
        "أين" to KnownLexiconEntry("Interrogative Adverb (Where)", "اسم استفهام للمكان", "স্থানবাচক প্রশ্নবোধক (কোথায়)", "Built on Fathah (مبني)", "প্রশ্নবোধক • অপরিবর্তনীয়", "Interrogative", "প্রশ্নবোধক"),
        "آمنوا" to KnownLexiconEntry("Form IV Perfect Verb (They believed)", "فعل ماضٍ (أَفْعَلَ - باب الإفعال)", "বাবে ইফ‘আল অতীত ক্রিয়া (তারা ঈমান এনেছে)", "Built on Dammah (مبني على الضم)", "বহুবচন অতীত ক্রিয়া", "Plural • 3rd Person Masc.", "বহুবচন • পুংলিঙ্গ"),
        "يعلمون" to KnownLexiconEntry("Imperfect Verb (They know)", "فعل مضارع (من الأفعال الخمسة)", "বর্তমান/ভবিষ্যৎ ক্রিয়া (তারা জানে)", "Indicative with Nun (مرفوع بثبوت النون)", "মারফু — নুন-সহ বর্তমান ক্রিয়া", "Plural • 3rd Person Masc.", "বহুবচন • পুংলিঙ্গ"),
        "يؤمنون" to KnownLexiconEntry("Form IV Imperfect Verb (They believe)", "فعل مضارع (يُفْعِلُ - باب الإفعال)", "বাবে ইফ‘আল বর্তমান ক্রিয়া (তারা বিশ্বাস করে)", "Indicative with Nun (مرفوع بثبوت النون)", "মারফু — নুন-সহ বর্তমান ক্রিয়া", "Plural • 3rd Person Masc.", "বহুবচন • পুংলিঙ্গ"),
        "المتقين" to KnownLexiconEntry("Definite Active Participle (God-conscious)", "اسم فاعل معرف بأل (مُتَّقِي)", "নির্দিষ্ট কর্তৃবাচক বিশেষণ (মুত্তাকীগণ)", "Genitive / Accusative Pl. (بالياء)", "মাজরুর / মানসুব বহুবচন", "Plural • Masculine", "বহুবচন • পুংলিঙ্গ"),
        "المؤمنين" to KnownLexiconEntry("Definite Active Participle (Believers)", "اسم فاعل معرف بأل (مُؤْمِن)", "নির্দিষ্ট কর্তৃবাচক বিশেষণ (মুমিনগণ)", "Genitive / Accusative Pl. (بالياء)", "মাজরুর / মানসুব বহুবচন", "Plural • Masculine", "বহুবচন • পুংলিঙ্গ"),
        "المؤمنون" to KnownLexiconEntry("Definite Active Participle (Believers)", "اسم فاعل معرف بأل (مُؤْمِن)", "নির্দিষ্ট কর্তৃবাচক বিশেষণ (মুমিনগণ)", "Nominative Pl. (مرفوع بالواو)", "মারফু — কর্তৃকারক বহুবচন", "Plural • Masculine", "বহুবচন • পুংলিঙ্গ"),
        "الكافرين" to KnownLexiconEntry("Definite Active Participle (Disbelievers)", "اسم فاعل معرف بأل (كَافِر)", "নির্দিষ্ট কর্তৃবাচক বিশেষণ (কাফিরগণ)", "Genitive / Accusative Pl. (بالياء)", "মাজরুর / মানসুব বহুবচন", "Plural • Masculine", "বহুবচন • পুংলিঙ্গ"),
        "السماوات" to KnownLexiconEntry("Definite Sound Fem. Plural (Heavens)", "جمع مؤنث سالم معرف بأل", "নির্দিষ্ট স্ত্রী-বহুবচন বিশেষ্য (আকাশমণ্ডলী)", "Kasrah for Genitive/Accusative", "বহুবচন বিশেষ্য", "Plural • Feminine", "বহুবচন • স্ত্রীলিঙ্গ"),
        "الأرض" to KnownLexiconEntry("Definite Noun (The Earth)", "اسم معرف بأل", "নির্দিষ্ট বিশেষ্য (পৃথিবী)", "Contextual Inflection", "বাক্যগত ই‘রাব", "Singular • Feminine (Sama'i)", "একবচন • স্ত্রীলিঙ্গ"),
        "الكتاب" to KnownLexiconEntry("Definite Noun (The Book / Scripture)", "اسم معرف بأل (فِعَال)", "নির্দিষ্ট বিশেষ্য (কিতাব/গ্রন্থ)", "Contextual Inflection", "বাক্যগত ই‘রাব", "Singular • Masculine", "একবচন • পুংলিঙ্গ"),
        "العالمين" to KnownLexiconEntry("Definite Plural Noun (All Worlds)", "ملحق بجمع المذكر السالم", "নির্দিষ্ট বহুবচন বিশেষ্য (সকল জগৎ)", "Genitive / Accusative Pl. (بالياء)", "মাজরুর / মানসুব বহুবচন", "Plural • Masculine", "বহুবচন • পুংলিঙ্গ"),
        "الرحمن" to KnownLexiconEntry("Intensive Attribute (Entirely Merciful)", "صيغة مبالغة (فَعْلَان)", "অতিশয় গুণবাচক বিশেষণ (পরম দয়ালু)", "Contextual Inflection", "বাক্যগত ই‘রাব", "Singular • Intensive", "একবচন • অতিশয়"),
        "الرحيم" to KnownLexiconEntry("Resembling Adjective (Especially Merciful)", "صفة مشبهة (فَعِيل)", "স্থায়ী গুণবাচক বিশেষণ (অসীম দয়ালু)", "Contextual Inflection", "বাক্যগত ই‘রাব", "Singular • Permanent Attribute", "একবচন • স্থায়ী গুণ")
    )

    private val MUQATTAAT_SET = setOf(
        "الم", "المص", "الر", "المر", "كهيعص", "طه", "طسم", "طس", "يس", "ص", "حم", "عسق", "ق", "ن"
    )

    fun analyze(
        word: WordEntity,
        translations: List<WordTranslationEntity>
    ): DetailedWordAnalysis {
        val rawArabic = word.textUthmani.trim().replace(WAQF_MARKS_REGEX, "")
        val bareWord = stripDiacriticsAndAlefWasla(rawArabic)

        if (bareWord in MUQATTAAT_SET) {
            val segment = WordSegmentPart(
                arabicPrefixOrPart = rawArabic,
                roleLabel = "Quranic Disconnected Letters (Huruf Muqatta'at)",
                arabicRoleLabel = "الحروف المقطعة",
                banglaRoleLabel = "হুরূফে মুকাত্তাআত (বিচ্ছিন্ন অক্ষর)",
                roleType = SegmentRoleType.STEM
            )
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
                        else -> tr.languageCode.uppercase()
                    }
                    langLabel to tr.meaningText
                }
            return DetailedWordAnalysis(
                partOfSpeechEn = "Quranic Disconnected Letters (Huruf Muqatta'at)",
                partOfSpeechAr = "الحروف المقطعة",
                partOfSpeechBn = "হুরূফে মুকাত্তাআত (বিচ্ছিন্ন অক্ষর)",
                grammaticalCaseOrMood = "Invariable / Letters of the Quran (مبني لا إعراب له)",
                grammaticalCaseBn = "অপরিবর্তনীয় / ই‘রাববিহীন পবিত্র অক্ষর",
                numberAndGender = "Quranic Initial Letters",
                numberAndGenderBn = "কুরআনের সূচনার বিচ্ছিন্ন অক্ষর",
                segments = listOf(segment),
                spacedRoot = "",
                stemArabic = rawArabic,
                explanationEn = "The letters \"$rawArabic\" appear among the Quranic Disconnected Letters (Huruf Muqatta'at) at the opening of several Surahs. Their precise inner meanings are known to Allah alone (علمها عند الله), symbolizing the miraculous linguistic challenge (I'jaz) of the Quran.",
                explanationBn = "«$rawArabic» পবিত্র কুরআনের বিভিন্ন সূরার শুরুতে অবতীর্ণ হুরূফে মুকাত্তাআত বা বিচ্ছিন্ন অক্ষরসমূহের অন্তর্ভুক্ত। এগুলোর প্রকৃত তাৎপর্য কেবল আল্লাহ তাআলাই সম্যক অবগত (علمها عند الله), এবং এগুলো কুরআনের ভাষার অনুপম অলৌকিকতা (ই‘জাজ) প্রমাণ করে।",
                explanationAr = "الحروف المقطعة «$rawArabic» في أوائل السور. وعلمها عند الله وحده، وهي تشير إلى إعجاز القرآن وبلاغته.",
                allMeanings = orderedMeanings,
                normalizedStem = bareWord,
                sarfPatternEn = "Quranic Disconnected Letters (Huruf Muqatta'at)",
                sarfPatternAr = "الحروف المقطعة",
                sarfPatternBn = "হুরূফে মুকাত্তাআত",
                sarfNoteEn = "Sacred initial letters opening the Surah. Not derived from a root verb.",
                sarfNoteBn = "সূরার সূচনাকারী পবিত্র অক্ষর। কোনো ধাতুমূল থেকে সাধিত নয়।",
                nahwFunctionEn = "Independent Initial Constituent (مبتدأ مقدر أو فاتحة)",
                nahwFunctionBn = "সূচনাকারী স্বতন্ত্র বাক্যাংশ (ফাতিহা)",
                definitenessEn = "Proper Letter Entity",
                definitenessBn = "নির্দিষ্ট অক্ষর সত্তা"
            )
        }

        val tag = word.grammarTagJson.trim()
        val lower = tag.lowercase()
        val enMeaning = translations.firstOrNull { it.languageCode.equals("en", true) }?.meaningText.orEmpty()
        val bnMeaning = translations.firstOrNull { it.languageCode.equals("bn", true) }?.meaningText.orEmpty()

        val extraction = deriveWordSegments(rawArabic, lower)
        val known = KNOWN_FUNCTION_WORDS[bareWord]
            ?: KNOWN_FUNCTION_WORDS[stripDiacriticsAndAlefWasla(extraction.stem)]

        val hasPrepPrefix = extraction.prefixes.any { it.contains("بِ") || it.contains("لِ") || it.contains("كَ") }
        val hasConjPrefix = extraction.prefixes.any { it.contains("وَ") || it.contains("فَ") }
        val hasDefiniteArticle = extraction.prefixes.any { it.contains("ال") || it.contains("ٱل") }
        val hasSuffix = extraction.suffixes.isNotEmpty()

        // 1. Part of Speech (Explicit tag > Known lexicon > Morphological & Meaning heuristics)
        val (posEn, posAr, posBn) = when {
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
            lower.isNotBlank() -> Triple("Noun / Substantive (Ism)", "اسم", "বিশেষ্য পদ")
            // Untagged word (Surahs 2-114) intelligent inference
            known != null && extraction.prefixes.isEmpty() -> Triple(known.posEn, known.posAr, known.posBn)
            bareWord == "لله" || bareWord == "بالله" || bareWord == "والله" || bareWord == "تالله" ->
                Triple("Preposition/Oath + Proper Noun (Allah)", "لفظ الجلالة مع حرف", "অব্যয় ও নামবাচক বিশেষ্য (আল্লাহ্)")
            hasPrepPrefix && hasDefiniteArticle ->
                Triple("Preposition + Definite Noun", "حرف جر + اسم معرف", "সম্বন্ধসূচক অব্যয় ও নির্দিষ্ট বিশেষ্য")
            hasPrepPrefix ->
                Triple("Preposition + Noun / Pronoun", "جار ومجرور", "অব্যয় ও বিশেষ্য/সর্বনাম")
            hasDefiniteArticle && hasConjPrefix ->
                Triple("Conjunction + Definite Noun", "حرف عطف + اسم معرف", "যোজক ও নির্দিষ্ট বিশেষ্য")
            hasDefiniteArticle ->
                Triple("Definite Noun (Ism Ma'rifah)", "اسم معرف بأل", "নির্দিষ্ট বিশেষ্য (আল্-যুক্ত)")
            extraction.prefixes.any { it.contains("سَ") } ->
                Triple("Future Prefix + Imperfect Verb", "حرف استقبال + فعل مضارع", "ভবিষ্যৎ অব্যয় ও ক্রিয়া")
            isLikelyImperfectVerb(extraction.stem, enMeaning) ->
                Triple("Imperfect Verb (Mudari')", "فعل مضارع", "বর্তমান বা ভবিষ্যৎ কালের ক্রিয়া")
            isLikelyImperativeVerb(extraction.stem, enMeaning) ->
                Triple("Imperative Verb (Amr)", "فعل أمر", "আদেশসূচক ক্রিয়া")
            hasConjPrefix && isLikelyVerbMeaning(enMeaning) ->
                Triple("Conjunction + Verb", "عطف وفعل", "যোজক অব্যয় ও ক্রিয়া")
            isLikelyVerbMeaning(enMeaning) || rawArabic.endsWith("ُوا") || rawArabic.endsWith("وا") ->
                Triple("Verb (Fi'l)", "فعل", "ক্রিয়াপদ")
            hasConjPrefix && known != null ->
                Triple("Conjunction + ${known.posEn}", "عطف + ${known.posAr}", "যোজক + ${known.posBn}")
            hasSuffix ->
                Triple("Noun / Stem + Attached Pronoun", "اسم / كلمة + ضمير متصل", "শব্দমূল ও যুক্ত সর্বনাম")
            else -> Triple("Noun / Substantive (Ism)", "اسم", "বিশেষ্য পদ")
        }

        // 2. Case / Mood inference
        val lastHarakat = findLastVowelMark(rawArabic)
        val (caseEn, caseBn) = when {
            lower.contains("genitive") -> "Genitive (مجرور / Majrur)" to "মাজরুর — সম্বন্ধ/জেরযুক্ত অবস্থা (مجرور)"
            lower.contains("nominative") -> "Nominative (مرفوع / Marfu')" to "মারফু — কর্তৃকারক/পেশযুক্ত অবস্থা (مرفوع)"
            lower.contains("accusative") -> "Accusative (منصوب / Mansub)" to "মানসুব — কর্মকারক/যবরযুক্ত অবস্থা (منصوب)"
            lower.contains("jussive") -> "Jussive (مجزوم / Majzum)" to "মাজযুম — সুকুন/জযমযুক্ত অবস্থা (مجزوم)"
            lower.contains("subjunctive") -> "Subjunctive (منصوب / Mansub)" to "মানসুব — যবরযুক্ত ক্রিয়া অবস্থা (منصوب)"
            lower.contains("indicative") -> "Indicative (مرفوع / Marfu')" to "মারফু — সাধারণ বর্তমান ক্রিয়া (مرفوع)"
            known != null && extraction.prefixes.isEmpty() -> known.caseEn to known.caseBn
            hasPrepPrefix || lastHarakat == '\u0650' || lastHarakat == '\u064D' ->
                "Genitive (مجرور / Majrur)" to "মাজরুর — জের/কাসরাহযুক্ত অবস্থা (مجرور)"
            lastHarakat == '\u064F' || lastHarakat == '\u064C' || rawArabic.endsWith("ونَ") || rawArabic.endsWith("انِ") ->
                "Nominative (مرفوع / Marfu')" to "মারফু — পেশ/দাম্মাহযুক্ত অবস্থা (مرفوع)"
            lastHarakat == '\u064E' || lastHarakat == '\u064B' || rawArabic.endsWith("اً") ->
                "Accusative (منصوب / Mansub)" to "মানসুব — যবর/ফাতহাহযুক্ত অবস্থা (منصوب)"
            lastHarakat == '\u0652' && (posAr.contains("فعل")) ->
                "Jussive (مجزوم / Majzum)" to "মাজযুম — সুকুনযুক্ত ক্রিয়া অবস্থা (مجزوم)"
            else -> "Contextual Inflection (إعراب السياق)" to "বাক্যের প্রসঙ্গ অনুযায়ী ই‘রাব (إعراب السياق)"
        }

        // 3. Number & Gender inference
        val (numGenderEn, numGenderBn) = inferNumberAndGender(rawArabic, extraction, lower, known)

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

        val (expEn, expBn, expAr) = buildExplanations(
            rawArabic = rawArabic,
            word = word,
            posEn = posEn,
            posAr = posAr,
            posBn = posBn,
            caseEn = caseEn,
            caseBn = caseBn,
            numGenderEn = numGenderEn,
            numGenderBn = numGenderBn,
            segments = extraction.segments,
            enMeaning = enMeaning,
            bnMeaning = bnMeaning
        )

        val normStem = BidiUtils.normalizeArabicForSearch(extraction.stem)
        val sarf = deriveSarfInfo(
            rawArabic = rawArabic,
            stemArabic = extraction.stem,
            bareWord = bareWord,
            posEn = posEn,
            posAr = posAr,
            rootArabic = spacedRoot,
            segments = extraction.segments,
            lowerGrammar = lower
        )
        val nahw = deriveNahwInfo(
            posEn = posEn,
            posAr = posAr,
            caseEn = caseEn,
            hasPrepPrefix = hasPrepPrefix,
            hasDefiniteArticle = hasDefiniteArticle,
            hasSuffix = hasSuffix,
            bareWord = bareWord
        )

        return DetailedWordAnalysis(
            partOfSpeechEn = posEn,
            partOfSpeechAr = posAr,
            partOfSpeechBn = posBn,
            grammaticalCaseOrMood = caseEn,
            grammaticalCaseBn = caseBn,
            numberAndGender = numGenderEn,
            numberAndGenderBn = numGenderBn,
            segments = extraction.segments,
            spacedRoot = spacedRoot,
            stemArabic = extraction.stem,
            explanationEn = expEn,
            explanationBn = expBn,
            explanationAr = expAr,
            allMeanings = orderedMeanings,
            normalizedStem = normStem,
            sarfPatternEn = sarf.patternEn,
            sarfPatternAr = sarf.patternAr,
            sarfPatternBn = sarf.patternBn,
            sarfNoteEn = sarf.noteEn,
            sarfNoteBn = sarf.noteBn,
            nahwFunctionEn = nahw.roleEn,
            nahwFunctionBn = nahw.roleBn,
            definitenessEn = nahw.defEn,
            definitenessBn = nahw.defBn
        )
    }

    private data class SarfDetails(
        val patternEn: String,
        val patternAr: String,
        val patternBn: String,
        val noteEn: String,
        val noteBn: String
    )

    private data class NahwDetails(
        val roleEn: String,
        val roleBn: String,
        val defEn: String,
        val defBn: String
    )

    private fun deriveSarfInfo(
        rawArabic: String,
        stemArabic: String,
        bareWord: String,
        posEn: String,
        posAr: String,
        rootArabic: String,
        segments: List<WordSegmentPart>,
        lowerGrammar: String
    ): SarfDetails {
        val bareStem = stripDiacriticsAndAlefWasla(stemArabic)
        val prefixes = segments.filter { it.roleType == SegmentRoleType.PREFIX }
        val suffixes = segments.filter { it.roleType == SegmentRoleType.SUFFIX }
        val rootCount = rootArabic.split(" ").count { it.isNotBlank() }

        val (patEn, patAr, patBn) = when {
            lowerGrammar.contains("form iv") || (posAr.contains("فعل") && (stemArabic.startsWith("أَ") || stemArabic.startsWith("يُ") || bareStem.startsWith("مُ"))) ->
                Triple("Form IV (If'al — أَفْعَلَ / يُفْعِلُ)", "باب الإفعال (أَفْعَلَ)", "বাবে ইফ‘আল (أَفْعَلَ / يُفْعِلُ)")
            lowerGrammar.contains("form ii") || (posAr.contains("فعل") && stemArabic.contains("ّ") && bareStem.length in 3..4) ->
                Triple("Form II (Taf'il — فَعَّلَ / يُفَعِّلُ)", "باب التفعيل (فَعَّلَ)", "বাবে তাফ‘ঈল (فَعَّلَ)")
            lowerGrammar.contains("form x") || bareStem.startsWith("است") || bareStem.startsWith("مست") ->
                Triple("Form X (Istif'al — اِسْتَفْعَلَ)", "باب الاستفعال (اِسْتَفْعَلَ)", "বাবে ইস্তিফ‘আল (اِسْتَفْعَلَ)")
            lowerGrammar.contains("active participle") || (bareStem.startsWith("م") && posEn.contains("Participle")) ->
                Triple("Active Participle (Ism Fa'il — فَاعِل / مُفْعِل)", "اسم الفاعل", "কর্তৃবাচক বিশেষণ (ইসমে ফা‘ইল)")
            lowerGrammar.contains("passive participle") ->
                Triple("Passive Participle (Ism Maf'ul — مَفْعُول)", "اسم المفعول", "কর্মবাচক বিশেষণ (ইসমে মাফ‘উল)")
            bareStem == "رحمن" ->
                Triple("Intensive Pattern (Fa'lan — فَعْلَان)", "صيغة مبالغة: فَعْلَان", "অতিশয় ওযন (فَعْلَان)")
            bareStem == "رحيم" || bareStem == "عليم" || bareStem == "حكيم" || bareStem == "كريم" || bareStem == "عظيم" || bareStem == "بصير" || bareStem == "سميع" ->
                Triple("Resembling Adjective (Fa'il — فَعِيل)", "صفة مشبهة: فَعِيل", "স্থায়ী গুণবাচক ওযন (فَعِيل)")
            posAr.contains("فعل أمر") ->
                Triple("Imperative Form (Fi'l Amr — اِفْعَلْ)", "صيغة الأمر", "আদেশসূচক রূপ (ফে‘লে আমর)")
            posAr.contains("فعل مضارع") ->
                Triple("Imperfect Form (Mudari' — يَفْعَلُ)", "الفعل المضارع", "বর্তমান/ভবিষ্যৎ ক্রিয়া রূপ (মুদারি)")
            posAr.contains("فعل ماض") ->
                Triple("Perfect Form (Madi — فَعَلَ)", "الفعل الماضي", "অতীত ক্রিয়া রূপ (মাদি)")
            posAr.contains("فعل") ->
                Triple("Verbal Stem (Fi'l)", "صيغة فعلية", "ক্রিয়ামূল (ফে‘ল)")
            posEn.contains("Particle") || posEn.contains("Preposition") && bareWord in KNOWN_FUNCTION_WORDS ->
                Triple("Invariable Particle (Harf Mabni)", "حرف مبني غير متصرف", "অপরিবর্তনীয় অব্যয় (মাবনী)")
            bareWord == "الله" || bareWord == "لله" || bareWord == "بالله" || bareWord == "والله" ->
                Triple("Proper Divine Name (Lafz al-Jalalah)", "لفظ الجلالة", "লাফযে জালালাহ (আল্লাহ নাম)")
            rootCount == 3 ->
                Triple("Triliteral Stem (Thulathi — ثُلَاثِي)", "جذر ثلاثي", "তিন-অক্ষরবিশিষ্ট ধাতুমূল (সুলাসী)")
            rootCount == 4 ->
                Triple("Quadriliteral Stem (Ruba'i — رُبَاعِي)", "جذر رباعي", "চার-অক্ষরবিশিষ্ট ধাতুমূল (রুবা‘ঈ)")
            else ->
                Triple("Nominal / Derived Stem (Ism)", "كلمة اسمية / مشتقة", "নামপদ / সাধিত শব্দ (ইসম)")
        }

        val affixesCount = prefixes.size + suffixes.size
        val noteEn = buildString {
            append("Stem \"$stemArabic\" (${countArabicBaseLetters(stemArabic)} base letters).")
            if (affixesCount > 0) {
                append(" Built with ${prefixes.size} prefix(es) and ${suffixes.size} suffix(es).")
            } else {
                append(" Unaffixed standalone word form.")
            }
            if (rootArabic.isNotBlank()) {
                append(" Derived from verified root [$rootArabic].")
            }
        }
        val noteBn = buildString {
            append("মূল কাণ্ড «$stemArabic» (${BidiUtils.formatNumber(countArabicBaseLetters(stemArabic), "bn")}টি মূল বর্ণ)।")
            if (affixesCount > 0) {
                append(" এতে ${BidiUtils.formatNumber(prefixes.size, "bn")}টি পূর্বপ্রত্যয় ও ${BidiUtils.formatNumber(suffixes.size, "bn")}টি পরপ্রত্যয় যুক্ত হয়েছে।")
            } else {
                append(" কোনো বাড়তি উপসর্গ/প্রত্যয় ছাড়াই স্বতন্ত্র রূপ।")
            }
            if (rootArabic.isNotBlank()) {
                append(" স্বীকৃত ধাতুমূল: [$rootArabic]।")
            }
        }

        return SarfDetails(patEn, patAr, patBn, noteEn, noteBn)
    }

    private fun deriveNahwInfo(
        posEn: String,
        posAr: String,
        caseEn: String,
        hasPrepPrefix: Boolean,
        hasDefiniteArticle: Boolean,
        hasSuffix: Boolean,
        bareWord: String
    ): NahwDetails {
        val isVerb = posAr.contains("فعل")
        val isParticle = posEn.startsWith("Particle") || (posEn.startsWith("Preposition") && bareWord in KNOWN_FUNCTION_WORDS)

        val (defEn, defBn) = when {
            isVerb -> "Verbal Clause Head (فعل)" to "ক্রিয়াপদ (ফেল)"
            isParticle -> "Invariant Particle (حرف)" to "অপরিবর্তনীয় অব্যয়"
            hasDefiniteArticle || bareWord == "الله" || bareWord == "لله" || posEn.contains("Proper Noun") || posEn.contains("Pronoun") ->
                "Definite (معرفة / Ma'rifah)" to "নির্দিষ্ট পদ (মা‘রিফাহ)"
            hasSuffix -> "Definite by Annexation (مضاف إلى ضمير)" to "সর্বনাম যুক্ত হওয়ায় নির্দিষ্ট (মুদাফ)"
            else -> "Indefinite / Context-Defined (نكرة أو مضاف)" to "অনির্দিষ্ট বা সম্বন্ধযুক্ত (নাকিরাহ/মুদাফ)"
        }

        val (roleEn, roleBn) = when {
            hasPrepPrefix ->
                "Prepositional Phrase (جار ومجرور): preposition governs genitive state." to
                    "জার ও মাজরুর (অব্যয় + সম্বন্ধ পদ): পূর্ববর্তী অব্যয়ের কারণে জের (কাসরাহ) হয়।"
            caseEn.contains("Nominative") && isVerb ->
                "Indicative Verb (مرفوع): expresses ongoing or habitual action." to
                    "মারফু ক্রিয়া: বর্তমান বা অভ্যস্ত ক্রিয়া প্রকাশ করে।"
            caseEn.contains("Nominative") ->
                "Nominative Role (مرفوع): subject (Fa'il), topic (Mubtada), or predicate (Khabar)." to
                    "মারফু পদ: বাক্যে কর্তা (ফা‘ইল), উদ্দেশ্য (মুবতাদা) বা বিধেয় (খবর) রূপে ব্যবহৃত।"
            caseEn.contains("Accusative") && !isVerb ->
                "Accusative Role (منصوب): direct object (Maf'ul), state (Hal), or governed by Inna." to
                    "মানসুব পদ: কর্মকারক (মাফ‘উল), অবস্থা (হাল) বা ইন্না-র ইসম রূপে ব্যবহৃত।"
            caseEn.contains("Subjunctive") ->
                "Subjunctive Verb (فعل منصوب): governed by particle such as أن / لن." to
                    "মানসুব ক্রিয়া: মানসুবকারক অব্যয়ের প্রভাবে শেষ বর্ণে যবর (ফাতহাহ) হয়েছে।"
            caseEn.contains("Jussive") ->
                "Jussive / Imperative (مجزوم / أمر): governed by لم or command form." to
                    "মাজযুম / আমর ক্রিয়া: সুকুন বা জযমযুক্ত আদেশ/না-বোধক অবস্থা।"
            caseEn.contains("Genitive") ->
                "Genitive Role (مجرور): governed by preposition or second term of Idafah." to
                    "মাজরুর পদ: হরফে জার বা ইদাফাত (সম্বন্ধ পদের) কারণে জেরযুক্ত অবস্থা।"
            else ->
                "Syntactic position (محل إعرابي) determined by surrounding clause." to
                    "বাক্যের পূর্বাপর গঠন অনুযায়ী ই‘রাব ও অবস্থান নির্ধারিত।"
        }

        return NahwDetails(roleEn, roleBn, defEn, defBn)
    }

    private fun stripDiacriticsAndAlefWasla(text: String): String {
        return text.replace(DIACRITIC_REGEX, "")
            .replace('ٱ', 'ا')
            .replace('ى', 'ي')
            .trim()
    }

    private fun countArabicBaseLetters(text: String): Int {
        return text.count { ch -> ch in '\u0621'..'\u064A' || ch == 'ٱ' || ch == 'ٰ' }
    }

    private fun findLastVowelMark(arabic: String): Char? {
        for (i in arabic.length - 1 downTo 0) {
            val ch = arabic[i]
            if (ch in '\u064B'..'\u0652') return ch
        }
        return null
    }

    private fun isLikelyImperfectVerb(stem: String, enMeaning: String): Boolean {
        val bare = stripDiacriticsAndAlefWasla(stem)
        val firstChar = bare.firstOrNull() ?: return false
        if (firstChar !in setOf('ي', 'ت', 'ن', 'أ')) return false
        if (bare.startsWith("ال")) return false
        val hasMudariVowel = stem.startsWith("يَ") || stem.startsWith("يُ") ||
            stem.startsWith("تَ") || stem.startsWith("تُ") ||
            stem.startsWith("نَ") || stem.startsWith("نُ")
        val lowerEn = enMeaning.lowercase()
        val verbCue = lowerEn.contains("he ") || lowerEn.contains("they ") ||
            lowerEn.contains("you ") || lowerEn.contains("we ") ||
            lowerEn.contains("will ") || lowerEn.contains("does ")
        return hasMudariVowel || (bare.length >= 4 && verbCue)
    }

    private fun isLikelyImperativeVerb(stem: String, enMeaning: String): Boolean {
        val lowerEn = enMeaning.lowercase().trim()
        val hasCommandCue = lowerEn.startsWith("say") || lowerEn.startsWith("read") ||
            lowerEn.startsWith("establish") || lowerEn.startsWith("remember") ||
            lowerEn.startsWith("fear") || lowerEn.startsWith("worship") ||
            lowerEn.startsWith("seek") || lowerEn.startsWith("guide")
        return hasCommandCue && !lowerEn.startsWith("he ") && !lowerEn.startsWith("they ")
    }

    private fun isLikelyVerbMeaning(enMeaning: String): Boolean {
        val lower = enMeaning.lowercase().trim()
        return lower.startsWith("he ") || lower.startsWith("she ") ||
            lower.startsWith("they ") || lower.startsWith("you ") ||
            lower.startsWith("we ") || lower.contains("created") ||
            lower.contains("sent down") || lower.contains("believed") ||
            lower.contains("guided") || lower.contains("said")
    }

    private fun inferNumberAndGender(
        rawArabic: String,
        extraction: SegmentExtractionResult,
        lowerGrammar: String,
        known: KnownLexiconEntry?
    ): Pair<String, String> {
        if (lowerGrammar.isNotBlank()) {
            val numEn = when {
                lowerGrammar.contains("plural") -> "Plural (جمع)"
                lowerGrammar.contains("dual") -> "Dual (مثنى)"
                else -> "Singular (مفرد)"
            }
            val genEn = when {
                lowerGrammar.contains("feminine") -> "Feminine (مؤنث)"
                else -> "Masculine (مذكر)"
            }
            val numBn = when {
                lowerGrammar.contains("plural") -> "বহুবচন (جمع)"
                lowerGrammar.contains("dual") -> "দ্বিবচন (مثنى)"
                else -> "একবচন (مفرد)"
            }
            val genBn = when {
                lowerGrammar.contains("feminine") -> "স্ত্রীলিঙ্গ (مؤنث)"
                else -> "পুংলিঙ্গ (مذكر)"
            }
            return "$numEn • $genEn" to "$numBn • $genBn"
        }

        if (known != null && extraction.suffixes.isEmpty()) {
            return known.numGenderEn to known.numGenderBn
        }

        val bare = stripDiacriticsAndAlefWasla(rawArabic)
        val sfxCombined = extraction.suffixes.joinToString(" ")
        val isPlural = sfxCombined.contains("Pl.") ||
            bare.endsWith("ون") || bare.endsWith("ين") || bare.endsWith("ات") ||
            bare.endsWith("وا") || bare.endsWith("هم") || bare.endsWith("كم")
        val isDual = sfxCombined.contains("Dual") ||
            bare.endsWith("ان") || bare.endsWith("هما") || bare.endsWith("كما")
        val isFeminine = sfxCombined.contains("Fem.") ||
            bare.endsWith("ة") || bare.endsWith("ات") || bare.endsWith("ها") ||
            bare.endsWith("هن") || bare.endsWith("كن")

        val numEn = when {
            isDual -> "Dual (مثنى)"
            isPlural -> "Plural (جمع)"
            else -> "Singular (مفرد)"
        }
        val genEn = if (isFeminine) "Feminine (مؤنث)" else "Masculine (مذكر)"

        val numBn = when {
            isDual -> "দ্বিবচন (مثنى)"
            isPlural -> "বহুবচন (جمع)"
            else -> "একবচন (مفرد)"
        }
        val genBn = if (isFeminine) "স্ত্রীলিঙ্গ (مؤنث)" else "পুংলিঙ্গ (مذكر)"

        return "$numEn • $genEn" to "$numBn • $genBn"
    }

    private data class SegmentExtractionResult(
        val segments: List<WordSegmentPart>,
        val prefixes: List<String>,
        val stem: String,
        val suffixes: List<String>
    )

    private fun deriveWordSegments(arabic: String, lowerGrammar: String): SegmentExtractionResult {
        val parts = mutableListOf<WordSegmentPart>()
        val prefixTags = mutableListOf<String>()
        val suffixTags = mutableListOf<String>()
        var stem = arabic.trim().replace(WAQF_MARKS_REGEX, "")
        val bareFull = stripDiacriticsAndAlefWasla(stem)

        val isKnownStandalonePrepositionOrWord = bareFull in setOf("في", "من", "على", "إلى", "عن", "بين", "كل", "بل", "كان", "قال", "قل", "لم", "لن", "قد", "لا", "ما")

        if (!isKnownStandalonePrepositionOrWord) {
            // 1. Conjunction proclitics: وَـ / فَـ (explicit grammar OR high-confidence diacritic check with enough remaining base letters)
            val hasExplicitConj = lowerGrammar.contains("conjunction")
            if ((hasExplicitConj && (stem.startsWith("و") || stem.startsWith("ف"))) ||
                ((stem.startsWith("وَ") || stem.startsWith("فَ")) && countArabicBaseLetters(stem) >= 3)
            ) {
                var cut = 1
                while (cut < stem.length && stem[cut] in '\u064B'..'\u065F') cut++
                val conj = stem.substring(0, cut)
                val isWa = conj.startsWith("و")
                parts.add(
                    WordSegmentPart(
                        arabicPrefixOrPart = "${conj}ـ",
                        roleLabel = if (isWa) "Prefix: Conjunction (and)" else "Prefix: Conjunction (then/so)",
                        arabicRoleLabel = "حرف عطف",
                        banglaRoleLabel = if (isWa) "উপসর্গ: সংযোজক (এবং)" else "উপসর্গ: সংযোজক (অতঃপর)",
                        roleType = SegmentRoleType.PREFIX
                    )
                )
                prefixTags.add(conj)
                stem = stem.substring(cut)
            }

            // 2. Special merged لِلْـ / لِلَّـ -> لِـ + الْـ
            if (stem.startsWith("لِلْ") || stem.startsWith("لِلَّ") || stem.startsWith("لِل")) {
                if (countArabicBaseLetters(stem) >= 4) {
                    parts.add(
                        WordSegmentPart(
                            arabicPrefixOrPart = "لِـ",
                            roleLabel = "Prefix: Preposition (for/to)",
                            arabicRoleLabel = "حرف جر: اللام",
                            banglaRoleLabel = "উপসর্গ: لِـ (জন্য)",
                            roleType = SegmentRoleType.PREFIX
                        )
                    )
                    prefixTags.add("لِ")
                    stem = "ٱل" + stem.removePrefix("لِلْ").removePrefix("لِلَّ").removePrefix("لِل")
                }
            } else {
                // 3. Preposition / Particle proclitics: بِـ / لِـ / كَـ / سَـ
                val hasExplicitPrep = lowerGrammar.contains("preposition")
                val minRemaining = if (hasExplicitPrep) 2 else 3
                val procliticMatch: Triple<String, String, Pair<String, String>>? = when {
                    stem.startsWith("بِ") && (hasExplicitPrep || countArabicBaseLetters(stem) >= minRemaining || bareFull.startsWith("به")) ->
                        Triple("بِ", "Prefix: Preposition (with/in/by)", "حرف جر: الباء" to "উপসর্গ: بِـ (দ্বারা/নামে)")
                    stem.startsWith("لِ") && (hasExplicitPrep || countArabicBaseLetters(stem) >= minRemaining) ->
                        Triple("لِ", "Prefix: Preposition (for/to)", "حرف جر: اللام" to "উপসর্গ: لِـ (জন্য)")
                    stem.startsWith("لَ") && countArabicBaseLetters(stem) >= 3 && !bareFull.startsWith("لكن") ->
                        Triple("لَ", "Prefix: Emphatic Lam (surely)", "لام التوكيد" to "উপসর্গ: নিশ্চয়তাবোধক (অবশ্যই)")
                    stem.startsWith("كَ") && countArabicBaseLetters(stem) >= 3 ->
                        Triple("كَ", "Prefix: Preposition (like/as)", "حرف تشبيه وجر" to "উপসর্গ: كَـ (মত)")
                    stem.startsWith("سَ") && countArabicBaseLetters(stem) >= 4 ->
                        Triple("سَ", "Prefix: Future Particle (will)", "حرف استقبال" to "উপসর্গ: سَـ (শীঘ্রই)")
                    else -> null
                }
                if (procliticMatch != null) {
                    val (pfx, enLbl, arBn) = procliticMatch
                    parts.add(
                        WordSegmentPart(
                            arabicPrefixOrPart = "${pfx}ـ",
                            roleLabel = enLbl,
                            arabicRoleLabel = arBn.first,
                            banglaRoleLabel = arBn.second,
                            roleType = SegmentRoleType.PREFIX
                        )
                    )
                    prefixTags.add(pfx)
                    stem = stem.removePrefix(pfx)
                }
            }

            // 4. Definite Article Al- (ٱلْ / ٱل / الْ / ال)
            if (stem.startsWith("ٱلْ") || stem.startsWith("ٱل") || stem.startsWith("الْ") || stem.startsWith("ال")) {
                if (countArabicBaseLetters(stem) >= 4) {
                    val alPrefix = when {
                        stem.startsWith("ٱلْ") -> "ٱلْ"
                        stem.startsWith("ٱل") -> "ٱل"
                        stem.startsWith("الْ") -> "الْ"
                        else -> "ال"
                    }
                    val bareStemAfterAl = stripDiacriticsAndAlefWasla(stem.removePrefix(alPrefix))
                    // Avoid stripping "ال" inside relative pronouns الذي/الذين/التي
                    if (bareStemAfterAl !in setOf("ذي", "ذين", "تي", "لاتي")) {
                        parts.add(
                            WordSegmentPart(
                                arabicPrefixOrPart = "${alPrefix}ـ",
                                roleLabel = "Prefix: Definite Article (Al-)",
                                arabicRoleLabel = "أداة تعريف (ال)",
                                banglaRoleLabel = "নির্দিষ্টতাজ্ঞাপক (আল্)",
                                roleType = SegmentRoleType.PREFIX
                            )
                        )
                        prefixTags.add(alPrefix)
                        stem = stem.removePrefix(alPrefix)
                    }
                }
            }
        }

        // 5. Enclitic Suffix extraction (Pronouns, Plural/Feminine endings)
        val suffixRule = detectSuffix(stem, lowerGrammar)
        if (suffixRule != null) {
            stem = suffixRule.remainingStem
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

        if (suffixRule != null) {
            parts.add(suffixRule.segment)
            suffixTags.add(suffixRule.segment.roleLabel)
        }

        return SegmentExtractionResult(
            segments = parts,
            prefixes = prefixTags,
            stem = stem.ifBlank { arabic },
            suffixes = suffixTags
        )
    }

    private data class SuffixMatch(
        val remainingStem: String,
        val segment: WordSegmentPart
    )

    private fun detectSuffix(stem: String, lowerGrammar: String): SuffixMatch? {
        val rules = listOf(
            Triple(listOf("كُمَا"), "ـكُمَا", Triple("Pronoun Suffix: you two (2nd Dual)", "ضمير متصل (مثنى)", "সর্বনাম: তোমাদের দুজনের")),
            Triple(listOf("هُمَا", "هِمَا"), "ـهُمَا", Triple("Pronoun Suffix: both of them (3rd Dual)", "ضمير متصل (مثنى)", "সর্বনাম: তাদের দুজনের")),
            Triple(listOf("كُمْ"), "ـكُمْ", Triple("Pronoun Suffix: you / your (2nd Pl.)", "ضمير متصل: كُمْ", "সর্বনাম: তোমাদের/তোমরা")),
            Triple(listOf("كُنَّ"), "ـكُنَّ", Triple("Pronoun Suffix: you (2nd Fem. Pl.)", "ضمير متصل: كُنَّ", "সর্বনাম: তোমাদের (স্ত্রী)")),
            Triple(listOf("هُمْ", "هِمْ"), "ـهُمْ", Triple("Pronoun Suffix: them / their (3rd Pl.)", "ضمير متصل: هُمْ", "সর্বনাম: তাদের/তারা")),
            Triple(listOf("هُنَّ", "هِنَّ"), "ـهُنَّ", Triple("Pronoun Suffix: them (3rd Fem. Pl.)", "ضمير متصل: هُنَّ", "সর্বনাম: তাদের (স্ত্রী)")),
            Triple(listOf("نَا"), "ـنَا", Triple("Pronoun Suffix: us / our (1st Pl.)", "ضمير متصل: نَا", "সর্বনাম: আমাদের/আমরা")),
            Triple(listOf("هَا"), "ـهَا", Triple("Pronoun Suffix: her / it (3rd Fem. Sg.)", "ضمير متصل: هَا", "সর্বনাম: তার/এটি (স্ত্রী)")),
            Triple(listOf("هُ", "هِ"), "ـهُ / ـهِ", Triple("Pronoun Suffix: him / it (3rd Masc. Sg.)", "ضمير متصل: هُ/هِ", "সর্বনাম: তাঁর/তাকে/এটি")),
            Triple(listOf("كَ"), "ـكَ", Triple("Pronoun Suffix: you / your (2nd Masc. Sg.)", "ضمير متصل: كَ", "সর্বনাম: তোমার/আপনার")),
            Triple(listOf("كِ"), "ـكِ", Triple("Pronoun Suffix: you / your (2nd Fem. Sg.)", "ضمير متصل: كِ", "সর্বনাম: তোমার (স্ত্রী)")),
            Triple(listOf("ونَ"), "ـونَ", Triple("Plural Marker (Masculine Pl.)", "علامة جمع المذكر", "বহুবচন প্রত্যয় (-উনা)")),
            Triple(listOf("ينَ"), "ـينَ", Triple("Plural Marker (Oblique Masc. Pl.)", "علامة جمع المذكر", "বহুবচন প্রত্যয় (-ঈনা)")),
            Triple(listOf("اتِ", "اتٌ", "اتٍ", "اتُ"), "ـات", Triple("Feminine Plural Marker (Fem. Pl.)", "علامة جمع المؤنث", "স্ত্রী বহুবচন প্রত্যয় (-আত)")),
            Triple(listOf("ةِ", "ةٌ", "ةٍ", "ةُ", "ةً"), "ـة", Triple("Feminine Marker (Taa Marbutah Fem.)", "تاء التأنيث", "স্ত্রীলিঙ্গ চিহ্ন (তা মারবূতা)"))
        )

        val baseCount = countArabicBaseLetters(stem)
        val forcePronoun = lowerGrammar.contains("attached pronoun") || lowerGrammar.contains("possessive")

        for ((endings, displaySfx, labels) in rules) {
            for (end in endings) {
                if (stem.endsWith(end)) {
                    val remainder = stem.removeSuffix(end)
                    val remBases = countArabicBaseLetters(remainder)
                    // Ensure stem still has valid base letters (e.g., بِهِ has stem بِـ with 1 base letter, normal nouns need >=2)
                    val minRem = if (forcePronoun || end in setOf("هُمْ", "هِمْ", "كُمْ", "هَا", "هُ", "هِ", "نَا")) 1 else 2
                    if (remBases >= minRem && baseCount >= 2) {
                        return SuffixMatch(
                            remainingStem = remainder,
                            segment = WordSegmentPart(
                                arabicPrefixOrPart = displaySfx,
                                roleLabel = labels.first,
                                arabicRoleLabel = labels.second,
                                banglaRoleLabel = labels.third,
                                roleType = SegmentRoleType.SUFFIX
                            )
                        )
                    }
                }
            }
        }

        if (forcePronoun) {
            return SuffixMatch(
                remainingStem = stem,
                segment = WordSegmentPart(
                    arabicPrefixOrPart = "ـضَمِير",
                    roleLabel = "Attached Pronoun Suffix",
                    arabicRoleLabel = "ضمير متصل",
                    banglaRoleLabel = "যুক্ত সর্বনাম",
                    roleType = SegmentRoleType.SUFFIX
                )
            )
        }
        return null
    }

    private fun buildExplanations(
        rawArabic: String,
        word: WordEntity,
        posEn: String,
        posAr: String,
        posBn: String,
        caseEn: String,
        caseBn: String,
        numGenderEn: String,
        numGenderBn: String,
        segments: List<WordSegmentPart>,
        enMeaning: String,
        bnMeaning: String
    ): Triple<String, String, String> {
        val prefixes = segments.filter { it.roleType == SegmentRoleType.PREFIX }
        val stemPart = segments.firstOrNull { it.roleType == SegmentRoleType.STEM }
        val suffixes = segments.filter { it.roleType == SegmentRoleType.SUFFIX }

        val meaningLineEn = enMeaning.ifBlank { word.transliteration }
        val segDescEn = buildString {
            append("\"$rawArabic\" (${word.transliteration}) functions as $posEn ($posAr).")
            if (prefixes.isNotEmpty() || suffixes.isNotEmpty()) {
                append(" Structure: ")
                val parts = mutableListOf<String>()
                prefixes.forEach { parts.add("${it.arabicPrefixOrPart} (${it.roleLabel.removePrefix("Prefix: ")})") }
                stemPart?.let { parts.add("${it.arabicPrefixOrPart} (Stem)") }
                suffixes.forEach { parts.add("${it.arabicPrefixOrPart} (${it.roleLabel})") }
                append(parts.joinToString(" + "))
                append(".")
            }
            if (word.rootArabic.isNotBlank()) {
                append(" Root letters: ${word.rootArabic}.")
            }
            if (meaningLineEn.isNotBlank()) {
                append(" Meaning in context: \"$meaningLineEn\".")
            }
            append(" Grammar: $caseEn • $numGenderEn.")
        }

        val segDescBn = buildString {
            append("«$rawArabic» (${word.transliteration}) একটি $posBn ($posAr)।")
            if (prefixes.isNotEmpty() || suffixes.isNotEmpty()) {
                append(" গঠন বিশ্লেষণ: ")
                val parts = mutableListOf<String>()
                prefixes.forEach { parts.add("${it.arabicPrefixOrPart} (${it.banglaRoleLabel})") }
                stemPart?.let { parts.add("${it.arabicPrefixOrPart} (${it.banglaRoleLabel})") }
                suffixes.forEach { parts.add("${it.arabicPrefixOrPart} (${it.banglaRoleLabel})") }
                append(parts.joinToString(" + "))
                append("।")
            }
            if (word.rootArabic.isNotBlank()) {
                append(" মূল ধাতু (মাদ্দাহ): ${word.rootArabic}।")
            }
            if (bnMeaning.isNotBlank()) {
                append(" শাব্দিক অর্থ: «$bnMeaning»।")
            }
            append(" ব্যাকরণগত অবস্থা: $caseBn; বচন ও লিঙ্গ: $numGenderBn।")
        }

        val caseAr = when {
            caseEn.contains("Genitive") -> "مجرور"
            caseEn.contains("Nominative") -> "مرفوع"
            caseEn.contains("Accusative") || caseEn.contains("Subjunctive") -> "منصوب"
            caseEn.contains("Jussive") -> "مجزوم"
            else -> "حسب السياق"
        }
        val numAr = when {
            numGenderEn.contains("Plural") -> "جمع"
            numGenderEn.contains("Dual") -> "مثنى"
            else -> "مفرد"
        }
        val genderAr = if (numGenderEn.contains("Feminine")) "مؤنث" else "مذكر"

        val segDescAr = buildString {
            append("الكلمة «$rawArabic» هي $posAr.")
            if (word.rootArabic.isNotBlank()) {
                append(" الجذر: ${word.rootArabic}.")
            }
            append(" الإعراب والحالة: $caseAr • $numAr • $genderAr.")
        }

        return Triple(segDescEn, segDescBn, segDescAr)
    }
}
