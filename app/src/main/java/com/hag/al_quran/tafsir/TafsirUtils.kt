package com.hag.al_quran.tafsir

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

object TafsirUtils {

    private const val TAG = "TafsirUtils"

    // HTTP client بمهلات + إعادة محاولة
    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /** قراءة نصّ الملف مع إزالة BOM إن وُجد */
    private fun readTextNoBom(file: File): String {
        var s = FileInputStream(file).use { it.readBytes() }.toString(Charset.forName("UTF-8"))
        if (s.isNotEmpty() && s[0] == '\uFEFF') s = s.substring(1)
        return s
    }

    /** هل المفتاح على شكل "سورة:آية" (مع احتمال وجود أصفار بادئة)؟ */
    private fun isSurahAyahKey(k: String): Boolean {
        val p = k.split(":")
        if (p.size != 2) return false
        val s = p[0].toIntOrNull()
        val a = p[1].toIntOrNull()
        return s != null && s >= 1 && a != null && a >= 1
    }

    /** جرّب استخراج النص من أي جسم آية محتمل */
    private fun extractAyahTextFromAny(any: Any?): String? {
        if (any == null) return null
        if (any is String) return if (any.isBlank()) null else any

        if (any is JSONObject) {
            val keys = arrayOf("text", "tafseer", "tafseer_text", "tafsir", "value", "content")
            for (k in keys) {
                val v = any.optString(k, null)
                if (!v.isNullOrBlank()) return v
            }
            val body = any.optJSONObject("body")
            if (body != null) {
                for (k in keys) {
                    val v = body.optString(k, null)
                    if (!v.isNullOrBlank()) return v
                }
            }
            val data = any.optJSONObject("data")
            if (data != null) {
                for (k in keys) {
                    val v = data.optString(k, null)
                    if (!v.isNullOrBlank()) return v
                }
            }
            return null
        }

        if (any is JSONArray) {
            // إن كانت مصفوفة آيات، خذ العنصر الأول كهروب سريع
            if (any.length() > 0) {
                val first = any.opt(0)
                return extractAyahTextFromAny(first)
            }
        }
        return null
    }

    /** جرّب عدة تراكيب محتملة لاستخراج نص آية واحدة من كائن سورة */
    private fun extractFromSurahNode(surahNode: Any?, ayah: Int): String? {
        if (surahNode == null) return null

        if (surahNode is JSONObject) {
            val aKey = ayah.toString()

            // مباشرة: "1": "نص" أو "1": {text:"..."}
            val direct = surahNode.opt(aKey)
            val directTxt = extractAyahTextFromAny(direct)
            if (!directTxt.isNullOrBlank()) return directTxt

            // ayahs كـ مصفوفة أو كائن
            if (surahNode.has("ayahs")) {
                val ayahsAny = surahNode.opt("ayahs")
                if (ayahsAny is JSONArray) {
                    // ابحث بالعَدَد، ثم بالـ index
                    for (i in 0 until ayahsAny.length()) {
                        val o = ayahsAny.optJSONObject(i) ?: continue
                        val num = o.optInt("ayah", o.optInt("aya", -1))
                        if (num == ayah) {
                            val t = extractAyahTextFromAny(o)
                            if (!t.isNullOrBlank()) return t
                            val t2 = o.optString("text", null)
                            if (!t2.isNullOrBlank()) return t2
                        }
                    }
                    val idx = ayah - 1
                    if (idx >= 0 && idx < ayahsAny.length()) {
                        val t = extractAyahTextFromAny(ayahsAny.opt(idx))
                        if (!t.isNullOrBlank()) return t
                    }
                } else if (ayahsAny is JSONObject) {
                    val any = ayahsAny.opt(aKey)
                    val t = extractAyahTextFromAny(any)
                    if (!t.isNullOrBlank()) return t
                }
            }

            // أسماء أخرى للمصفوفة
            val candidates = arrayOf("verses", "ayas", "ayat")
            for (arrKey in candidates) {
                if (surahNode.has(arrKey)) {
                    val arr = surahNode.optJSONArray(arrKey)
                    if (arr != null) {
                        val idx = ayah - 1
                        if (idx >= 0 && idx < arr.length()) {
                            val t = extractAyahTextFromAny(arr.opt(idx))
                            if (!t.isNullOrBlank()) return t
                        }
                    }
                }
            }
        }

        if (surahNode is JSONArray) {
            val idx = ayah - 1
            if (idx >= 0 && idx < surahNode.length()) {
                val t = extractAyahTextFromAny(surahNode.opt(idx))
                if (!t.isNullOrBlank()) return t
            }
        }

        return null
    }

    /** قراءة نص آية من ملف محلي — يدعم معظم الأشكال */
    fun getAyahTafsir(context: Context, surah: Int, ayah: Int, tafsirFile: String): String? {
        return try {
            val file = File(context.filesDir, tafsirFile)
            if (!file.exists()) {
                Log.e(TAG, "File not found: $tafsirFile")
                return null
            }

            val txt = readTextNoBom(file).trim()
            if (txt.isEmpty()) return null

            // 0) خريطة مسطّحة مباشرة: {"1:1":"..."} (نستخدم حلقة while بدل forEach)
            if (txt.startsWith("{")) {
                val obj = JSONObject(txt)

                var flatFound: String? = null
                val iterFlat = obj.keys()
                while (iterFlat.hasNext()) {
                    val k = iterFlat.next()
                    if (isSurahAyahKey(k)) {
                        val key1 = "$surah:$ayah"
                        val key2 = "${surah.toString().padStart(3, '0')}:${ayah.toString().padStart(3, '0')}"
                        val v = obj.optString(key1, obj.optString(key2, null))
                        if (!v.isNullOrBlank()) {
                            flatFound = v
                        }
                        break
                    }
                }
                if (!flatFound.isNullOrBlank()) return flatFound

                // 1) داخل data.surahs
                val data = obj.optJSONObject("data")
                if (data != null) {
                    val surahsObj = data.optJSONObject("surahs")
                    if (surahsObj != null) {
                        val sKey = surah.toString()
                        var node: Any? = surahsObj.opt(sKey)
                        if (node == null) node = surahsObj.opt(surah.toString().padStart(3, '0'))
                        val t = extractFromSurahNode(node, ayah)
                        if (!t.isNullOrBlank()) return t
                    }
                    // 2) داخل data مباشرة
                    val sKey2 = surah.toString()
                    var node2: Any? = data.opt(sKey2)
                    if (node2 == null) node2 = data.opt(surah.toString().padStart(3, '0'))
                    val t2 = extractFromSurahNode(node2, ayah)
                    if (!t2.isNullOrBlank()) return t2
                }

                // 3) داخل surahs جذرية
                val surahs = obj.optJSONObject("surahs")
                if (surahs != null) {
                    val sKey = surah.toString()
                    var node: Any? = surahs.opt(sKey)
                    if (node == null) node = surahs.opt(surah.toString().padStart(3, '0'))
                    val t = extractFromSurahNode(node, ayah)
                    if (!t.isNullOrBlank()) return t
                }

                // 4) مفاتيح سورة مباشرة "1": {...} أو "001": {...}
                var node3: Any? = obj.opt(surah.toString())
                if (node3 == null) node3 = obj.opt(surah.toString().padStart(3, '0'))
                val t3 = extractFromSurahNode(node3, ayah)
                if (!t3.isNullOrBlank()) return t3

                // 5) بعض الملفات تضع الآيات كمفاتيح عليا (نادر)
                val node4 = obj.opt(ayah.toString())
                val t4 = extractAyahTextFromAny(node4)
                if (!t4.isNullOrBlank()) return t4
            }

            // 6) جذر مصفوفة: [سور]
            if (txt.startsWith("[")) {
                val arr = JSONArray(txt)
                val sIdx = surah - 1
                if (sIdx >= 0 && sIdx < arr.length()) {
                    val surahNode = arr.opt(sIdx)
                    val t = extractFromSurahNode(surahNode, ayah)
                    if (!t.isNullOrBlank()) return t
                }
            }

            // 7) Fallback شامل: امسح أي هيكل عام — بدون لامبدا
            deepScanForAyah(txt, surah, ayah)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing tafsir ($tafsirFile): ${e.message}", e)
            null
        }
    }

    // بدّل الدالة القديمة بهذه
    private fun deepScanForAyah(raw: String, surah: Int, ayah: Int): String? {
        try {
            fun scan(any: Any?, curS: Int?): String? {
                if (any == null) return null
                when (any) {
                    is JSONObject -> {
                        val it = any.keys()
                        while (it.hasNext()) {
                            val k = it.next()
                            val v = any.opt(k)

                            val kNum = k.toIntOrNull()
                            if (kNum != null) {
                                // مفتاح رقمي: سورة أو آية حسب العمق
                                if (curS == null) {
                                    val r = scan(v, kNum) // سورة محتملة
                                    if (r != null) return r
                                } else {
                                    if (kNum == ayah) {
                                        val t = extractAyahTextFromAny(v)
                                        if (!t.isNullOrBlank()) return t
                                    }
                                    val r2 = scan(v, curS)
                                    if (r2 != null) return r2
                                }
                            } else {
                                // "سورة:آية"
                                if (isSurahAyahKey(k)) {
                                    val parts = k.split(":")
                                    val s = parts[0].toIntOrNull()
                                    val a = parts[1].toIntOrNull()
                                    if (s == surah && a == ayah) {
                                        val t = extractAyahTextFromAny(v)
                                        if (!t.isNullOrBlank()) return t
                                    }
                                }

                                // ayahs/verses/ayat كمصفوفة
                                if ((k.equals("ayahs", true) || k.equals("verses", true) || k.equals("ayat", true)) && v is JSONArray) {
                                    val idx = ayah - 1
                                    if (idx >= 0 && idx < v.length()) {
                                        val t = extractAyahTextFromAny(v.opt(idx))
                                        if (!t.isNullOrBlank()) return t
                                    }
                                }

                                val r3 = scan(v, curS)
                                if (r3 != null) return r3
                            }
                        }
                    }
                    is JSONArray -> {
                        for (i in 0 until any.length()) {
                            val v = any.opt(i)
                            if (v is JSONObject) {
                                val s = v.optInt("sura", v.optInt("surah", v.optInt("s", curS ?: 0)))
                                val a = v.optInt("ayah", v.optInt("aya", v.optInt("a", 0)))
                                if (s == surah && a == ayah) {
                                    val t = extractAyahTextFromAny(v)
                                    if (!t.isNullOrBlank()) return t
                                }
                            }
                            val r = scan(v, curS)
                            if (r != null) return r
                        }
                    }
                }
                return null
            }

            val root: Any = try { JSONObject(raw) } catch (_: JSONException) { JSONArray(raw) }
            return scan(root, null)
        } catch (_: Throwable) {
            return null
        }
    }


    // ===== التنزيل مع تحقق بعد الانتهاء =====
    fun downloadTafsirIfNeeded(
        context: Context,
        fileName: String,
        url: String,
        forceDownload: Boolean = false,
        callback: (Boolean, File?) -> Unit
    ) {
        val finalFile = File(context.filesDir, fileName)

        // موجود مسبقًا
        if (finalFile.exists() && finalFile.length() > 1024 && !forceDownload) {
            // تحقّق سريع: جرّب قراءة آية معروفة
            val ok = (getAyahTafsir(context, 1, 1, fileName) != null
                    || getAyahTafsir(context, 2, 1, fileName) != null)
            callback(ok, if (ok) finalFile else null)
            return
        }

        try {
            val req = Request.Builder()
                .url(url)
                .header("Accept-Encoding", "gzip")
                .build()

            http.newCall(req).execute().use { res ->
                if (!res.isSuccessful) { callback(false, null); return }
                val body = res.body ?: run { callback(false, null); return }
                val tmp = File(context.filesDir, "$fileName.part")

                tmp.sink().buffer().use { sink ->
                    sink.writeAll(body.source())
                }

                if (finalFile.exists()) finalFile.delete()
                tmp.renameTo(finalFile)
            }

            // تحقّق فوري بعد التنزيل
            val valid = (getAyahTafsir(context, 1, 1, fileName) != null
                    || getAyahTafsir(context, 2, 1, fileName) != null)
            if (!valid) finalFile.delete()
            callback(valid, if (valid) finalFile else null)

        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e.message}", e)
            callback(false, null)
        }
    }
}
