package com.hag.al_quran.tafsir

/**
 * مصدر واحد لقائمة التفاسير وروابط/أسماء الملفات.
 * يسهل الرجوع إليه من أي مكان (Manager / Utils / BottomSheet).
 */
object TafsirKit {

    // الاسم المعروض للمستخدم ↔ اسم ملف JSON على الـ CDN/التخزين
    private val items: List<Pair<String, String>> = listOf(
        "تفسير ابن كثير" to "ar-tafsir-ibn-kathir.json",
        "تفسير السعدي"  to "ar-tafsir-as-saadi.json",
        "تفسير القرطبي" to "ar-tafsir-al-qurtubi.json"
    )

    /** إرجاع كل أسماء التفاسير كـ Array (للاستخدام في AlertDialog مثلاً) */
    fun names(): Array<String> = items.map { it.first }.toTypedArray()

    /** إرجاع كل الملفات كـ Array */
    fun files(): Array<String> = items.map { it.second }.toTypedArray()

    /** جلب الاسم عند فهرس محدد */
    fun nameAt(index: Int): String = items.getOrNull(index)?.first ?: ""

    /** جلب الملف عند فهرس محدد */
    fun fileAt(index: Int): String = items.getOrNull(index)?.second ?: ""

    /** إيجاد الفهرس بناءً على اسم الملف */
    fun indexOfFile(fileName: String): Int =
        items.indexOfFirst { it.second == fileName }.takeIf { it >= 0 } ?: 0
}
