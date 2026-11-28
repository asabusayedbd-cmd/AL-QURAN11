package com.example.core.ibadah

/**
 * Comprehensive offline catalog of Bangladesh Divisions, all 64 Districts (Zilas),
 * major Upazilas/Municipalities, and Dhaka/Chattogram metropolitan areas with Bangla aliases
 * so prayer times and Qibla bearing resolve accurately anywhere in Bangladesh.
 */
object BangladeshLocations {

    val DISTRICTS_BY_DIVISION: Map<String, List<String>> = mapOf(
        "Dhaka" to listOf(
            "Dhaka", "Gazipur", "Narayanganj", "Narsingdi", "Munshiganj", "Manikganj",
            "Tangail", "Kishoreganj", "Faridpur", "Gopalganj", "Madaripur", "Rajbari", "Shariatpur"
        ),
        "Chattogram" to listOf(
            "Chattogram", "Cox's Bazar", "Cumilla", "Chandpur", "Brahmanbaria", "Noakhali",
            "Feni", "Lakshmipur", "Bandarban", "Rangamati", "Khagrachhari"
        ),
        "Sylhet" to listOf("Sylhet", "Moulvibazar", "Habiganj", "Sunamganj"),
        "Rajshahi" to listOf(
            "Rajshahi", "Bogura", "Pabna", "Sirajganj", "Natore", "Naogaon",
            "Chapainawabganj", "Joypurhat"
        ),
        "Khulna" to listOf(
            "Khulna", "Jashore", "Satkhira", "Bagerhat", "Kushtia", "Jhenaidah",
            "Chuadanga", "Meherpur", "Magura", "Narail"
        ),
        "Barishal" to listOf(
            "Barishal", "Patuakhali", "Bhola", "Pirojpur", "Jhalokati", "Barguna"
        ),
        "Rangpur" to listOf(
            "Rangpur", "Dinajpur", "Thakurgaon", "Panchagarh", "Nilphamari",
            "Lalmonirhat", "Kurigram", "Gaibandha"
        ),
        "Mymensingh" to listOf("Mymensingh", "Jamalpur", "Netrokona", "Sherpur")
    )

    private fun bd(
        en: String,
        bn: String,
        division: String,
        lat: Double,
        lon: Double,
        aliases: String = ""
    ) = CityLocationPreset(
        name = "$en, Bangladesh",
        latitude = lat,
        longitude = lon,
        banglaName = bn,
        region = "$division Division, Bangladesh",
        aliases = aliases,
        utcOffsetHours = 6.0
    )

    val ALL_PLACES: List<CityLocationPreset> = listOf(
        // ==================== DHAKA DIVISION (13 Districts + Metro & Upazilas) ====================
        bd("Dhaka", "ঢাকা", "Dhaka", 23.8103, 90.4125, "Dacca Capital"),
        bd("Mirpur, Dhaka", "মিরপুর, ঢাকা", "Dhaka", 23.8223, 90.3654),
        bd("Uttara, Dhaka", "উত্তরা, ঢাকা", "Dhaka", 23.8759, 90.3795),
        bd("Dhanmondi, Dhaka", "ধানমন্ডি, ঢাকা", "Dhaka", 23.7461, 90.3742),
        bd("Gulshan, Dhaka", "গুলশান, ঢাকা", "Dhaka", 23.7925, 90.4078),
        bd("Banani, Dhaka", "বনানী, ঢাকা", "Dhaka", 23.7937, 90.4066),
        bd("Mohammadpur, Dhaka", "মোহাম্মদপুর, ঢাকা", "Dhaka", 23.7662, 90.3589),
        bd("Motijheel, Dhaka", "মতিঝিল, ঢাকা", "Dhaka", 23.7330, 90.4172),
        bd("Old Dhaka (Lalbagh)", "পুরান ঢাকা (লালবাগ)", "Dhaka", 23.7190, 90.3881),
        bd("Badda, Dhaka", "বাড্ডা, ঢাকা", "Dhaka", 23.7805, 90.4267),
        bd("Jatrabari, Dhaka", "যাত্রাবাড়ী, ঢাকা", "Dhaka", 23.7104, 90.4349),
        bd("Demra, Dhaka", "ডেমরা, ঢাকা", "Dhaka", 23.7267, 90.4946),
        bd("Savar, Dhaka", "সাভার, ঢাকা", "Dhaka", 23.8583, 90.2667),
        bd("Ashulia, Dhaka", "আশুলিয়া, ঢাকা", "Dhaka", 23.8986, 90.3204),
        bd("Dhamrai, Dhaka", "ধামরাই, ঢাকা", "Dhaka", 23.9086, 90.2143),
        bd("Keraniganj, Dhaka", "কেরানীগঞ্জ, ঢাকা", "Dhaka", 23.6942, 90.3444),
        bd("Dohar, Dhaka", "দোহার, ঢাকা", "Dhaka", 23.5936, 90.1235),
        bd("Nawabganj, Dhaka", "নবাবগঞ্জ, ঢাকা", "Dhaka", 23.6601, 90.1726),

        bd("Gazipur", "গাজীপুর", "Dhaka", 23.9999, 90.4203),
        bd("Tongi, Gazipur", "টঙ্গী, গাজীপুর", "Dhaka", 23.8979, 90.4026),
        bd("Kaliakoir, Gazipur", "কালিয়াকৈর, গাজীপুর", "Dhaka", 24.0757, 90.2206),
        bd("Kapasia, Gazipur", "কাপাসিয়া, গাজীপুর", "Dhaka", 24.1003, 90.5736),
        bd("Sreepur, Gazipur", "শ্রীপুর, গাজীপুর", "Dhaka", 24.1991, 90.4664),
        bd("Kaliganj, Gazipur", "কালীগঞ্জ, গাজীপুর", "Dhaka", 23.9255, 90.5637),

        bd("Narayanganj", "নারায়ণগঞ্জ", "Dhaka", 23.6238, 90.5000),
        bd("Siddhirganj, Narayanganj", "সিদ্ধিরগঞ্জ, নারায়ণগঞ্জ", "Dhaka", 23.6717, 90.5171),
        bd("Sonargaon, Narayanganj", "সোনারগাঁও, নারায়ণগঞ্জ", "Dhaka", 23.6500, 90.6167),
        bd("Rupganj, Narayanganj", "রূপগঞ্জ, নারায়ণগঞ্জ", "Dhaka", 23.7927, 90.5284),
        bd("Araihazar, Narayanganj", "আড়াইহাজার, নারায়ণগঞ্জ", "Dhaka", 23.7885, 90.6518),

        bd("Narsingdi", "নরসিংদী", "Dhaka", 23.9322, 90.7151),
        bd("Madhabdi, Narsingdi", "মাধবদী, নরসিংদী", "Dhaka", 23.8495, 90.6744),
        bd("Palash, Narsingdi", "পলাশ, নরসিংদী", "Dhaka", 23.9502, 90.6316),
        bd("Raipura, Narsingdi", "রায়পুরা, নরসিংদী", "Dhaka", 23.9690, 90.8732),
        bd("Shibpur, Narsingdi", "শিবপুর, নরসিংদী", "Dhaka", 24.0419, 90.7429),

        bd("Munshiganj", "মুন্সীগঞ্জ", "Dhaka", 23.5422, 90.5305, "Bikrampur"),
        bd("Sreenagar, Munshiganj", "শ্রীনগর, মুন্সীগঞ্জ", "Dhaka", 23.5396, 90.2896),
        bd("Lohajang, Munshiganj", "লৌহজং, মুন্সীগঞ্জ", "Dhaka", 23.4675, 90.3422),

        bd("Manikganj", "মানিকগঞ্জ", "Dhaka", 23.8617, 90.0003),
        bd("Singair, Manikganj", "সিংগাইর, মানিকগঞ্জ", "Dhaka", 23.8165, 90.1510),
        bd("Shibalaya, Manikganj", "শিবালয়, মানিকগঞ্জ", "Dhaka", 23.8295, 89.7860, "Aricha Paturia"),

        bd("Tangail", "টাঙ্গাইল", "Dhaka", 24.2513, 89.9167),
        bd("Mirzapur, Tangail", "মির্জাপুর, টাঙ্গাইল", "Dhaka", 24.1029, 90.0972),
        bd("Madhupur, Tangail", "মধুপুর, টাঙ্গাইল", "Dhaka", 24.6184, 90.0258),
        bd("Sakhipur, Tangail", "সখীপুর, টাঙ্গাইল", "Dhaka", 24.3168, 90.1753),
        bd("Ghatail, Tangail", "ঘাটাইল, টাঙ্গাইল", "Dhaka", 24.4820, 89.9746),
        bd("Kalihati, Tangail", "কালিহাতী, টাঙ্গাইল", "Dhaka", 24.3833, 90.0083),
        bd("Bhuapur, Tangail", "ভুয়াপুর, টাঙ্গাইল", "Dhaka", 24.4622, 89.8691),

        bd("Kishoreganj", "কিশোরগঞ্জ", "Dhaka", 24.4449, 90.7766),
        bd("Bhairab, Kishoreganj", "ভৈরব, কিশোরগঞ্জ", "Dhaka", 24.0510, 90.9877),
        bd("Bajitpur, Kishoreganj", "বাজিতপুর, কিশোরগঞ্জ", "Dhaka", 24.2122, 90.9502),
        bd("Karimganj, Kishoreganj", "করিমগঞ্জ, কিশোরগঞ্জ", "Dhaka", 24.4623, 90.8768),
        bd("Pakundia, Kishoreganj", "পাকুন্দিয়া, কিশোরগঞ্জ", "Dhaka", 24.3248, 90.6775),

        bd("Faridpur", "ফরিদপুর", "Dhaka", 23.6071, 89.8429),
        bd("Bhanga, Faridpur", "ভাঙ্গা, ফরিদপুর", "Dhaka", 23.3853, 89.9841),
        bd("Boalmari, Faridpur", "বোয়ালমারী, ফরিদপুর", "Dhaka", 23.3802, 89.6873),

        bd("Gopalganj", "গোপালগঞ্জ", "Dhaka", 23.0051, 89.8266),
        bd("Tungipara, Gopalganj", "টুঙ্গিপাড়া, গোপালগঞ্জ", "Dhaka", 22.9001, 89.8869),
        bd("Muksudpur, Gopalganj", "মুকসুদপুর, গোপালগঞ্জ", "Dhaka", 23.3144, 89.8672),

        bd("Madaripur", "মাদারীপুর", "Dhaka", 23.1641, 90.1897),
        bd("Shibchar, Madaripur", "শিবচর, মাদারীপুর", "Dhaka", 23.3474, 90.1636),
        bd("Kalkini, Madaripur", "কালকিনি, মাদারীপুর", "Dhaka", 23.0719, 90.2442),

        bd("Rajbari", "রাজবাড়ী", "Dhaka", 23.7574, 89.6445),
        bd("Goalanda, Rajbari", "গোয়ালন্দ, রাজবাড়ী", "Dhaka", 23.7366, 89.7618, "Daulatdia"),
        bd("Pangsha, Rajbari", "পাংশা, রাজবাড়ী", "Dhaka", 23.7942, 89.4201),

        bd("Shariatpur", "শরীয়তপুর", "Dhaka", 23.2423, 90.4348),
        bd("Zajira, Shariatpur", "জাজিরা, শরীয়তপুর", "Dhaka", 23.3502, 90.3077),
        bd("Naria, Shariatpur", "নড়িয়া, শরীয়তপুর", "Dhaka", 23.2974, 90.4150),

        // ==================== CHATTOGRAM DIVISION (11 Districts + Upazilas) ====================
        bd("Chattogram", "চট্টগ্রাম", "Chattogram", 22.3569, 91.7832, "Chittagong Port City"),
        bd("Agrabad, Chattogram", "আগ্রাবাদ, চট্টগ্রাম", "Chattogram", 22.3237, 91.8124, "Chittagong"),
        bd("Patenga, Chattogram", "পতেঙ্গা, চট্টগ্রাম", "Chattogram", 22.2443, 91.7975, "Chittagong"),
        bd("Hathazari, Chattogram", "হাটহাজারী, চট্টগ্রাম", "Chattogram", 22.5091, 91.8077, "Chittagong"),
        bd("Sitakunda, Chattogram", "সীতাকুণ্ড, চট্টগ্রাম", "Chattogram", 22.6200, 91.6580),
        bd("Mirsharai, Chattogram", "মীরসরাই, চট্টগ্রাম", "Chattogram", 22.7717, 91.5761),
        bd("Patiya, Chattogram", "পটিয়া, চট্টগ্রাম", "Chattogram", 22.2952, 91.9794),
        bd("Raozan, Chattogram", "রাউজান, চট্টগ্রাম", "Chattogram", 22.5364, 91.9082),
        bd("Satkania, Chattogram", "সাতকানিয়া, চট্টগ্রাম", "Chattogram", 22.0785, 92.0493),
        bd("Lohagara, Chattogram", "লোহাগাড়া, চট্টগ্রাম", "Chattogram", 22.0205, 92.1045),
        bd("Sandwip, Chattogram", "সন্দ্বীপ, চট্টগ্রাম", "Chattogram", 22.4875, 91.4547),
        bd("Anwara, Chattogram", "আনোয়ারা, চট্টগ্রাম", "Chattogram", 22.2179, 91.9080),
        bd("Banshkhali, Chattogram", "বাঁশখালী, চট্টগ্রাম", "Chattogram", 22.0378, 91.9465),

        bd("Cox's Bazar", "কক্সবাজার", "Chattogram", 21.4272, 92.0058, "Coxs Bazar"),
        bd("Teknaf, Cox's Bazar", "টেকনাফ, কক্সবাজার", "Chattogram", 20.8644, 92.2981),
        bd("Ukhiya, Cox's Bazar", "উখিয়া, কক্সবাজার", "Chattogram", 21.2833, 92.1000),
        bd("Chakaria, Cox's Bazar", "চকরিয়া, কক্সবাজার", "Chattogram", 21.7610, 92.0772),
        bd("Maheshkhali, Cox's Bazar", "মহেশখালী, কক্সবাজার", "Chattogram", 21.5515, 91.9502),
        bd("St. Martin's Island, Cox's Bazar", "সেন্টমার্টিন, কক্সবাজার", "Chattogram", 20.6237, 92.3234),

        bd("Cumilla", "কুমিল্লা", "Chattogram", 23.4607, 91.1809, "Comilla"),
        bd("Daudkandi, Cumilla", "দাউদকান্দি, কুমিল্লা", "Chattogram", 23.5323, 90.7131, "Comilla"),
        bd("Chauddagram, Cumilla", "চৌদ্দগ্রাম, কুমিল্লা", "Chattogram", 23.2255, 91.3125),
        bd("Laksam, Cumilla", "লাকসাম, কুমিল্লা", "Chattogram", 23.2400, 91.1213),
        bd("Chandina, Cumilla", "চান্দিনা, কুমিল্লা", "Chattogram", 23.4857, 91.0079),
        bd("Debidwar, Cumilla", "দেবিদ্বার, কুমিল্লা", "Chattogram", 23.6001, 90.9922),
        bd("Muradnagar, Cumilla", "মুরাদনগর, কুমিল্লা", "Chattogram", 23.6355, 90.9355),

        bd("Chandpur", "চাঁদপুর", "Chattogram", 23.2333, 90.6713),
        bd("Hajiganj, Chandpur", "হাজীগঞ্জ, চাঁদপুর", "Chattogram", 23.2512, 90.8511),
        bd("Matlab, Chandpur", "মতলব, চাঁদপুর", "Chattogram", 23.3474, 90.7082),

        bd("Brahmanbaria", "ব্রাহ্মণবাড়িয়া", "Chattogram", 23.9571, 91.1119),
        bd("Ashuganj, Brahmanbaria", "আশুগঞ্জ, ব্রাহ্মণবাড়িয়া", "Chattogram", 24.0345, 91.0053),
        bd("Akhaura, Brahmanbaria", "আখাউড়া, ব্রাহ্মণবাড়িয়া", "Chattogram", 23.8825, 91.2116),
        bd("Nabinagar, Brahmanbaria", "নবীনগর, ব্রাহ্মণবাড়িয়া", "Chattogram", 23.8884, 90.9693),

        bd("Noakhali", "নোয়াখালী", "Chattogram", 22.8696, 91.0994, "Maijdee"),
        bd("Chowmuhani, Noakhali", "চৌমুহনী, নোয়াখালী", "Chattogram", 22.9437, 91.1257, "Begumganj"),
        bd("Chatkhil, Noakhali", "চাটখিল, নোয়াখালী", "Chattogram", 23.0499, 90.9585),
        bd("Hatiya, Noakhali", "হাতিয়া, নোয়াখালী", "Chattogram", 22.2833, 91.1250),

        bd("Feni", "ফেনী", "Chattogram", 23.0159, 91.3976),
        bd("Chhagalnaiya, Feni", "ছাগলনাইয়া, ফেনী", "Chattogram", 23.0419, 91.5150),
        bd("Daganbhuiyan, Feni", "দাগনভূঞা, ফেনী", "Chattogram", 22.9373, 91.3041),

        bd("Lakshmipur", "লক্ষ্মীপুর", "Chattogram", 22.9425, 90.8412),
        bd("Raipur, Lakshmipur", "রায়পুর, লক্ষ্মীপুর", "Chattogram", 23.0381, 90.7689),
        bd("Ramganj, Lakshmipur", "রামগঞ্জ, লক্ষ্মীপুর", "Chattogram", 23.1008, 90.8586),

        bd("Bandarban", "বান্দরবান", "Chattogram", 22.1953, 92.2184),
        bd("Lama, Bandarban", "লামা, বান্দরবান", "Chattogram", 21.7782, 92.1945),
        bd("Rangamati", "রাঙ্গামাটি", "Chattogram", 22.7324, 92.2985),
        bd("Kaptai, Rangamati", "কাপ্তাই, রাঙ্গামাটি", "Chattogram", 22.4959, 92.2266),
        bd("Khagrachhari", "খাগড়াছড়ি", "Chattogram", 23.1193, 91.9847),
        bd("Matiranga, Khagrachhari", "মাটিরাঙ্গা, খাগড়াছড়ি", "Chattogram", 23.0383, 91.8742),

        // ==================== SYLHET DIVISION (4 Districts + Upazilas) ====================
        bd("Sylhet", "সিলেট", "Sylhet", 24.8949, 91.8687),
        bd("Beanibazar, Sylhet", "বিয়ানীবাজার, সিলেট", "Sylhet", 24.8286, 92.1642),
        bd("Golapganj, Sylhet", "গোলাপগঞ্জ, সিলেট", "Sylhet", 24.8617, 92.0181),
        bd("Bishwanath, Sylhet", "বিশ্বনাথ, সিলেট", "Sylhet", 24.8129, 91.7600),
        bd("Zakiganj, Sylhet", "জকিগঞ্জ, সিলেট", "Sylhet", 24.8729, 92.3694),
        bd("Jaintiapur, Sylhet", "জৈন্তাপুর, সিলেট", "Sylhet", 25.1342, 92.1211),
        bd("Kanaighat, Sylhet", "কানাইঘাট, সিলেট", "Sylhet", 25.0046, 92.2666),
        bd("Companiganj, Sylhet", "কোম্পানীগঞ্জ, সিলেট", "Sylhet", 25.0757, 91.7518),

        bd("Moulvibazar", "মৌলভীবাজার", "Sylhet", 24.4829, 91.7774),
        bd("Sreemangal, Moulvibazar", "শ্রীমঙ্গল, মৌলভীবাজার", "Sylhet", 24.3065, 91.7296),
        bd("Kulaura, Moulvibazar", "কুলাউড়া, মৌলভীবাজার", "Sylhet", 24.5186, 92.0273),
        bd("Barlekha, Moulvibazar", "বড়লেখা, মৌলভীবাজার", "Sylhet", 24.7037, 92.1995),
        bd("Kamalganj, Moulvibazar", "কমলগঞ্জ, মৌলভীবাজার", "Sylhet", 24.3514, 91.8550),

        bd("Habiganj", "হবিগঞ্জ", "Sylhet", 24.3749, 91.4155),
        bd("Madhabpur, Habiganj", "মাধবপুর, হবিগঞ্জ", "Sylhet", 24.0992, 91.2921),
        bd("Nabiganj, Habiganj", "নবীগঞ্জ, হবিগঞ্জ", "Sylhet", 24.5680, 91.5150),
        bd("Chunarughat, Habiganj", "চুনারুঘাট, হবিগঞ্জ", "Sylhet", 24.1973, 91.5204),

        bd("Sunamganj", "সুনামগঞ্জ", "Sylhet", 25.0658, 91.3950),
        bd("Chhatak, Sunamganj", "ছাতক, সুনামগঞ্জ", "Sylhet", 25.0369, 91.6695),
        bd("Jagannathpur, Sunamganj", "জগন্নাথপুর, সুনামগঞ্জ", "Sylhet", 24.7697, 91.5450),
        bd("Tahirpur, Sunamganj", "তাহিরপুর, সুনামগঞ্জ", "Sylhet", 25.0928, 91.1743),

        // ==================== RAJSHAHI DIVISION (8 Districts + Upazilas) ====================
        bd("Rajshahi", "রাজশাহী", "Rajshahi", 24.3745, 88.6042),
        bd("Puthia, Rajshahi", "পুঠিয়া, রাজশাহী", "Rajshahi", 24.3652, 88.8342),
        bd("Godagari, Rajshahi", "গোদাগাড়ী, রাজশাহী", "Rajshahi", 24.4667, 88.3306),
        bd("Bagha, Rajshahi", "বাঘা, রাজশাহী", "Rajshahi", 24.1947, 88.8339),
        bd("Tanore, Rajshahi", "তানোর, রাজশাহী", "Rajshahi", 24.6020, 88.5831),

        bd("Bogura", "বগুড়া", "Rajshahi", 24.8465, 89.3773, "Bogra"),
        bd("Sherpur, Bogura", "শেরপুর, বগুড়া", "Rajshahi", 24.6710, 89.4209, "Bogra"),
        bd("Shibganj, Bogura", "শিবগঞ্জ, বগুড়া", "Rajshahi", 25.0157, 89.3177, "Mahasthangarh"),
        bd("Sariakandi, Bogura", "সারিয়াকান্দি, বগুড়া", "Rajshahi", 24.8881, 89.5666),

        bd("Pabna", "পাবনা", "Rajshahi", 24.0064, 89.2372),
        bd("Ishwardi, Pabna", "ঈশ্বরদী, পাবনা", "Rajshahi", 24.1286, 89.0657, "Rooppur"),
        bd("Bera, Pabna", "বেড়া, পাবনা", "Rajshahi", 24.0672, 89.6227),
        bd("Santhia, Pabna", "সাঁথিয়া, পাবনা", "Rajshahi", 24.0574, 89.5313),

        bd("Sirajganj", "সিরাজগঞ্জ", "Rajshahi", 24.4534, 89.7007),
        bd("Shahjadpur, Sirajganj", "শাহজাদপুর, সিরাজগঞ্জ", "Rajshahi", 24.1705, 89.5897),
        bd("Ullapara, Sirajganj", "উল্লাপাড়া, সিরাজগঞ্জ", "Rajshahi", 24.3167, 89.5667),
        bd("Belkuchi, Sirajganj", "বেলকুচি, সিরাজগঞ্জ", "Rajshahi", 24.2943, 89.6968),

        bd("Natore", "নাটোর", "Rajshahi", 24.4206, 89.0003),
        bd("Singra, Natore", "সিংড়া, নাটোর", "Rajshahi", 24.5008, 89.1436),
        bd("Lalpur, Natore", "লালপুর, নাটোর", "Rajshahi", 24.1843, 88.9721),

        bd("Naogaon", "নওগাঁ", "Rajshahi", 24.7936, 88.9318),
        bd("Mohadevpur, Naogaon", "মহাদেবপুর, নওগাঁ", "Rajshahi", 24.9163, 88.7429),
        bd("Patnitala, Naogaon", "পত্নীতলা, নওগাঁ", "Rajshahi", 25.0442, 88.7586),

        bd("Chapainawabganj", "চাঁপাইনবাবগঞ্জ", "Rajshahi", 24.5965, 88.2775, "Nawabganj"),
        bd("Shibganj, Chapainawabganj", "শিবগঞ্জ, চাঁপাইনবাবগঞ্জ", "Rajshahi", 24.6820, 88.1636),

        bd("Joypurhat", "জয়পুরহাট", "Rajshahi", 25.0968, 89.0227),
        bd("Panchbibi, Joypurhat", "পাঁচবিবি, জয়পুরহাট", "Rajshahi", 25.1887, 89.0209),

        // ==================== KHULNA DIVISION (10 Districts + Upazilas) ====================
        bd("Khulna", "খুলনা", "Khulna", 22.8456, 89.5403),
        bd("Dumuria, Khulna", "ডুমুরিয়া, খুলনা", "Khulna", 22.8091, 89.4235),
        bd("Phultala, Khulna", "ফুলতলা, খুলনা", "Khulna", 22.9737, 89.4581),
        bd("Paikgachha, Khulna", "পাইকগাছা, খুলনা", "Khulna", 22.5898, 89.3361),

        bd("Jashore", "যশোর", "Khulna", 23.1664, 89.2081, "Jessore"),
        bd("Benapole, Jashore", "বেনাপোল, যশোর", "Khulna", 23.0439, 88.8962, "Sharsha Jessore"),
        bd("Jhikargachha, Jashore", "ঝিকরগাছা, যশোর", "Khulna", 23.1003, 89.0977),
        bd("Noapara, Jashore", "নওয়াপাড়া, যশোর", "Khulna", 23.0282, 89.3924, "Abhaynagar"),

        bd("Satkhira", "সাতক্ষীরা", "Khulna", 22.7185, 89.0705),
        bd("Shyamnagar, Satkhira", "শ্যামনগর, সাতক্ষীরা", "Khulna", 22.3304, 89.1028, "Sundarbans"),
        bd("Kaliganj, Satkhira", "কালীগঞ্জ, সাতক্ষীরা", "Khulna", 22.4521, 89.0355),

        bd("Bagerhat", "বাগেরহাট", "Khulna", 22.6516, 89.7859),
        bd("Mongla, Bagerhat", "মোংলা, বাগেরহাট", "Khulna", 22.4875, 89.5919),
        bd("Fakirhat, Bagerhat", "ফকিরহাট, বাগেরহাট", "Khulna", 22.7793, 89.7073),

        bd("Kushtia", "কুষ্টিয়া", "Khulna", 23.9013, 89.1204),
        bd("Kumarkhali, Kushtia", "কুমারখালী, কুষ্টিয়া", "Khulna", 23.8548, 89.2392),
        bd("Bheramara, Kushtia", "ভেড়ামারা, কুষ্টিয়া", "Khulna", 24.0222, 88.9912),

        bd("Jhenaidah", "ঝিনাইদহ", "Khulna", 23.5448, 89.1539),
        bd("Kaliganj, Jhenaidah", "কালীগঞ্জ, ঝিনাইদহ", "Khulna", 23.4096, 89.1380),
        bd("Shailkupa, Jhenaidah", "শৈলকুপা, ঝিনাইদহ", "Khulna", 23.6889, 89.2512),

        bd("Chuadanga", "চুয়াডাঙ্গা", "Khulna", 23.6402, 88.8418),
        bd("Alamdanga, Chuadanga", "আলমডাঙ্গা, চুয়াডাঙ্গা", "Khulna", 23.7580, 88.9478),

        bd("Meherpur", "মেহেরপুর", "Khulna", 23.7622, 88.6318, "Mujibnagar"),
        bd("Gangni, Meherpur", "গাংনী, মেহেরপুর", "Khulna", 23.8165, 88.7495),

        bd("Magura", "মাগুরা", "Khulna", 23.4873, 89.4199),
        bd("Sreepur, Magura", "শ্রীপুর, মাগুরা", "Khulna", 23.6061, 89.3831),

        bd("Narail", "নড়াইল", "Khulna", 23.1725, 89.5127),
        bd("Lohagara, Narail", "লোহাগড়া, নড়াইল", "Khulna", 23.1856, 89.6476),

        // ==================== BARISHAL DIVISION (6 Districts + Upazilas) ====================
        bd("Barishal", "বরিশাল", "Barishal", 22.7010, 90.3535, "Barisal"),
        bd("Bakerganj, Barishal", "বাকেরগঞ্জ, বরিশাল", "Barishal", 22.5469, 90.3401),
        bd("Gournadi, Barishal", "গৌরনদী, বরিশাল", "Barishal", 22.9737, 90.2236),
        bd("Banaripara, Barishal", "বানারীপাড়া, বরিশাল", "Barishal", 22.7850, 90.1653),

        bd("Patuakhali", "পটুয়াখালী", "Barishal", 22.3596, 90.3299),
        bd("Kuakata, Patuakhali", "কুয়াকাটা, পটুয়াখালী", "Barishal", 21.8172, 90.1194, "Kalapara"),
        bd("Bauphal, Patuakhali", "বাউফল, পটুয়াখালী", "Barishal", 22.4267, 90.5174),

        bd("Bhola", "ভোলা", "Barishal", 22.6859, 90.6482),
        bd("Char Fasson, Bhola", "চরফ্যাশন, ভোলা", "Barishal", 22.1853, 90.7611),
        bd("Lalmohan, Bhola", "লালমোহন, ভোলা", "Barishal", 22.3376, 90.7375),

        bd("Pirojpur", "পিরোজপুর", "Barishal", 22.5791, 89.9759),
        bd("Mathbaria, Pirojpur", "মঠবাড়িয়া, পিরোজপুর", "Barishal", 22.2869, 89.9585),

        bd("Jhalokati", "ঝালকাঠি", "Barishal", 22.6406, 90.1987, "Jhalakathi"),
        bd("Nalchity, Jhalokati", "নলছিটি, ঝালকাঠি", "Barishal", 22.6331, 90.2684),

        bd("Barguna", "বরগুনা", "Barishal", 22.1592, 90.1264),
        bd("Amtali, Barguna", "আমতলী, বরগুনা", "Barishal", 22.1292, 90.2266),

        // ==================== RANGPUR DIVISION (8 Districts + Upazilas) ====================
        bd("Rangpur", "রংপুর", "Rangpur", 25.7439, 89.2752),
        bd("Pirganj, Rangpur", "পীরগঞ্জ, রংপুর", "Rangpur", 25.4146, 89.3183),
        bd("Mithapukur, Rangpur", "মিঠাপুকুর, রংপুর", "Rangpur", 25.5681, 89.2814),
        bd("Badarganj, Rangpur", "বদরগঞ্জ, রংপুর", "Rangpur", 25.6736, 89.0543),

        bd("Dinajpur", "দিনাজপুর", "Rangpur", 25.6279, 88.6332),
        bd("Parbatipur, Dinajpur", "পার্বতীপুর, দিনাজপুর", "Rangpur", 25.6626, 88.9189),
        bd("Birampur, Dinajpur", "বিরামপুর, দিনাজপুর", "Rangpur", 25.3833, 88.9833),
        bd("Setabganj, Dinajpur", "সেতাবগঞ্জ, দিনাজপুর", "Rangpur", 25.7985, 88.4582),

        bd("Thakurgaon", "ঠাকুরগাঁও", "Rangpur", 26.0336, 88.4617),
        bd("Pirganj, Thakurgaon", "পীরগঞ্জ, ঠাকুরগাঁও", "Rangpur", 25.8548, 88.3667),

        bd("Panchagarh", "পঞ্চগড়", "Rangpur", 26.3411, 88.5542),
        bd("Tetulia, Panchagarh", "তেতুলিয়া, পঞ্চগড়", "Rangpur", 26.4945, 88.3471, "Banglabandha"),

        bd("Nilphamari", "নীলফামারী", "Rangpur", 25.9318, 88.8560),
        bd("Saidpur, Nilphamari", "সৈয়দপুর, নীলফামারী", "Rangpur", 25.7777, 88.8917),
        bd("Domar, Nilphamari", "ডোমার, নীলফামারী", "Rangpur", 26.0981, 88.8286),

        bd("Lalmonirhat", "লালমনিরহাট", "Rangpur", 25.9923, 89.2847),
        bd("Patgram, Lalmonirhat", "পাটগ্রাম, লালমনিরহাট", "Rangpur", 26.3547, 89.0135, "Burimari"),

        bd("Kurigram", "কুড়িগ্রাম", "Rangpur", 25.8054, 89.6362),
        bd("Ulipur, Kurigram", "উলিপুর, কুড়িগ্রাম", "Rangpur", 25.6626, 89.6311),
        bd("Nageshwari, Kurigram", "নাগেশ্বরী, কুড়িগ্রাম", "Rangpur", 25.9681, 89.7073),

        bd("Gaibandha", "গাইবান্ধা", "Rangpur", 25.3288, 89.5281),
        bd("Gobindaganj, Gaibandha", "গোবিন্দগঞ্জ, গাইবান্ধা", "Rangpur", 25.1342, 89.3894),
        bd("Palashbari, Gaibandha", "পলাশবাড়ী, গাইবান্ধা", "Rangpur", 25.2804, 89.3490),

        // ==================== MYMENSINGH DIVISION (4 Districts + Upazilas) ====================
        bd("Mymensingh", "ময়মনসিংহ", "Mymensingh", 24.7471, 90.4203),
        bd("Trishal, Mymensingh", "ত্রিশাল, ময়মনসিংহ", "Mymensingh", 24.5819, 90.3944),
        bd("Bhaluka, Mymensingh", "ভালুকা, ময়মনসিংহ", "Mymensingh", 24.3749, 90.3788),
        bd("Muktagachha, Mymensingh", "মুক্তাগাছা, ময়মনসিংহ", "Mymensingh", 24.7647, 90.2564),
        bd("Gaffargaon, Mymensingh", "গফরগাঁও, ময়মনসিংহ", "Mymensingh", 24.4328, 90.5582),
        bd("Phulpur, Mymensingh", "ফুলপুর, ময়মনসিংহ", "Mymensingh", 24.9502, 90.3514),
        bd("Ishwarganj, Mymensingh", "ঈশ্বরগঞ্জ, ময়মনসিংহ", "Mymensingh", 24.6881, 90.6011),

        bd("Jamalpur", "জামালপুর", "Mymensingh", 24.9375, 89.9378),
        bd("Sarishabari, Jamalpur", "সরিষাবাড়ী, জামালপুর", "Mymensingh", 24.7429, 89.8325),
        bd("Dewanganj, Jamalpur", "দেওয়ানগঞ্জ, জামালপুর", "Mymensingh", 25.1502, 89.7667),
        bd("Islampur, Jamalpur", "ইসলামপুর, জামালপুর", "Mymensingh", 25.0833, 89.7833),

        bd("Netrokona", "নেত্রকোণা", "Mymensingh", 24.8709, 90.7279, "Netrakona"),
        bd("Kendua, Netrokona", "কেন্দুয়া, নেত্রকোণা", "Mymensingh", 24.6512, 90.8442),
        bd("Durgapur, Netrokona", "দুর্গাপুর, নেত্রকোণা", "Mymensingh", 25.1257, 90.6881),
        bd("Mohanganj, Netrokona", "মোহনগঞ্জ, নেত্রকোণা", "Mymensingh", 24.8698, 90.9675),

        bd("Sherpur", "শেরপুর", "Mymensingh", 25.0205, 90.0153),
        bd("Nalitabari, Sherpur", "নালিতাবাড়ী, শেরপুর", "Mymensingh", 25.0846, 90.1950),
        bd("Nakla, Sherpur", "নকলা, শেরপুর", "Mymensingh", 24.9833, 90.1833)
    )
}
