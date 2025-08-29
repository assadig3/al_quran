package com.hag.al_quran.search

class SearchIndex(private val ayat: List<AyahItem>) {

    data class AyahItem(
        val surah: Int,
        val ayah: Int,
        val page: Int,
        val text: String,
        val norm: String // النص بعد التطبيع وإزالة التشكيل (باستخدام SearchUtils.normalizeArabic/stripTashkeel)
    )

    // ================= بحث =================

    /** بحث جزئي (اختياري) */
    fun searchPartial(qRaw: String): List<AyahItem> {
        val q = SearchUtils.normalizeArabic(SearchUtils.stripTashkeel(qRaw))
        if (q.isEmpty()) return emptyList()
        return ayat.filter { it.norm.contains(q) }
    }

    /** بحث كلمة كاملة (المعتمد) */
    fun searchWholeWord(qRaw: String): List<AyahItem> {
        val q = SearchUtils.normalizeArabic(SearchUtils.stripTashkeel(qRaw))
        if (q.isEmpty()) return emptyList()
        return ayat.filter { containsWholeWord(it.norm, q) }
    }

    // حدود كلمة عربية بعد التطبيع
    private fun isArabicLetterNormalized(ch: Char): Boolean = ch in '\u0621'..'\u064A'

    private fun containsWholeWord(textNorm: String, queryNorm: String): Boolean {
        val qLen = queryNorm.length
        if (qLen == 0 || qLen > textNorm.length) return false
        var from = 0
        while (true) {
            val idx = textNorm.indexOf(queryNorm, from)
            if (idx < 0) return false
            val beforeOk = (idx == 0) || !isArabicLetterNormalized(textNorm[idx - 1])
            val afterPos = idx + qLen
            val afterOk = (afterPos >= textNorm.length) || !isArabicLetterNormalized(textNorm[afterPos])
            if (beforeOk && afterOk) return true
            from = idx + 1
        }
    }

    // ================= إحصاء مرات الذكر =================

    enum class CountMode {
        /** الكلمة نفسها فقط ككلمة مستقلة (لا يحتسب: والله/بالله/لله/فلله …) */
        WORD_ONLY,

        /** يحسب الكلمة + اللواصق الأحادية (و ف ب ك ل س ت) + خاصية استبدال (الـ) بـ (لل) للكلمات المعرّفة */
        WITH_PREFIXES
    }

    data class AyahCount(
        val surah: Int,
        val ayah: Int,
        val page: Int,
        val count: Int
    )

    data class CountSummary(
        val totalOccurrences: Int,  // إجمالي مرات الذكر
        val ayahMatches: Int,       // عدد الآيات التي وردت فيها
        val perAyah: List<AyahCount>
    )

    private val PREFIXES: Set<Char> = setOf('و','ف','ب','ك','ل','س','ت')

    /**
     * يعدّ مرات ظهور كلمة (أو صيغة اسم الجلالة) وفق النمط المطلوب.
     *
     * - WORD_ONLY: "الله" فقط ككلمة مستقلة.
     * - WITH_PREFIXES: يحسب أيضًا: والله، فالله، بالله، كالله، سالله، تالله،
     *   وكذلك حالة إدغام "ال" بعد لام الجر: لله، ولله، فلله، … (تعميم لأي كلمة تبدأ بـ"ال").
     */
    fun countOccurrences(queryRaw: String, mode: CountMode = CountMode.WORD_ONLY): CountSummary {
        val q = SearchUtils.normalizeArabic(SearchUtils.stripTashkeel(queryRaw))
        if (q.isBlank()) return CountSummary(0, 0, emptyList())

        // محضِّر أشكال بديلة في حال السماح باللواصق
        val alAlt: String? = if (mode == CountMode.WITH_PREFIXES && q.startsWith("ال")) {
            // استبدال "ال..." بـ "لل..." (مثال: الله -> لله، الرحمن -> للرحمن)
            "لل" + q.removePrefix("ال")
        } else null

        val per = ArrayList<AyahCount>()
        var grandTotal = 0

        for (a in ayat) {
            if (a.norm.isBlank()) continue
            var c = 0
            val tokens = a.norm.split(' ').filter { it.isNotBlank() }

            for (t in tokens) {
                // 1) الكلمة نفسها تمامًا
                if (t == q) { c++; continue }

                if (mode == CountMode.WITH_PREFIXES) {
                    // 2) بادئة حرف واحد من المجموعة + الكلمة (والله، بالله، …)
                    if (t.length == q.length + 1 && PREFIXES.contains(t[0]) && t.substring(1) == q) {
                        c++; continue
                    }
                    // 3) شكل "لل + بقية الكلمة" للكلمات التي تبدأ بـ"ال" (لله، للرحمن)
                    if (alAlt != null) {
                        if (t == alAlt) { c++; continue }
                        // 4) بادئة + (لل + بقية) مثل: ولله، فلله، وبالرحمن-> هذا الأخير لا ينطبق (لأنها ليست للـ)
                        if (t.length == alAlt.length + 1 && PREFIXES.contains(t[0]) && t.substring(1) == alAlt) {
                            c++; continue
                        }
                    }
                }
            }

            if (c > 0) {
                grandTotal += c
                per.add(AyahCount(a.surah, a.ayah, a.page, c))
            }
        }

        return CountSummary(
            totalOccurrences = grandTotal,
            ayahMatches = per.size,
            perAyah = per
        )
    }
}
