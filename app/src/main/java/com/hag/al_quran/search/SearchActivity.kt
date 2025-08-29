// File: app/src/main/java/com/hag/al_quran/search/SearchActivity.kt
package com.hag.al_quran.search

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.AutoCompleteTextView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hag.al_quran.QuranPageActivity
import com.hag.al_quran.R
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class SearchActivity : AppCompatActivity(), SearchResultAdapter.Listener {

    // -------- Views --------
    private lateinit var root: View
    private lateinit var input: EditText
    private lateinit var btnSearch: View
    private lateinit var progress: ProgressBar
    private lateinit var list: RecyclerView
    private val counterText by lazy { findViewById<TextView?>(R.id.resultCounter) }
    private val counterCard by lazy { findViewById<View?>(R.id.counterCard) }

    // -------- Adapter / تنفيذ --------
    private val adapter = SearchResultAdapter(this)
    private val handler = Handler(Looper.getMainLooper())
    private val bg = Executors.newSingleThreadExecutor()
    private val seq = AtomicInteger(0)
    private var debounceRunnable: Runnable? = null

    // -------- بيانات --------
    private lateinit var index: SearchIndex
    private lateinit var surahNames: List<String>
    private var indexReady = false

    // -------- اقتراحات تاريخ البحث (اختياري) --------
    private var suggestionsAdapter: ArrayAdapter<String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        // ربط الواجهات
        root = findViewById(R.id.searchRoot) ?: findViewById(android.R.id.content)
        input = findViewById(R.id.searchInput)
        btnSearch = findViewById(R.id.btnSearch)
        progress = findViewById(R.id.progress)
        list = findViewById(R.id.resultsList)

        // insets
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(v.paddingLeft, top + 12, v.paddingRight, v.paddingBottom)
            insets
        }

        // RecyclerView
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        list.setHasFixedSize(true)
        list.itemAnimator = null

        // اقتراحات التاريخ إن كان الإدخال AutoCompleteTextView (اختياري)
        (input as? AutoCompleteTextView)?.let { ac ->
            val history = SearchHistory.load(this)
            suggestionsAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, history)
            ac.setAdapter(suggestionsAdapter)
            ac.threshold = 1
            ac.setOnItemClickListener { _, _, pos, _ ->
                val chosen = suggestionsAdapter?.getItem(pos) ?: return@setOnItemClickListener
                ac.setText(chosen); ac.setSelection(chosen.length)
                startSearch(chosen)
            }
        }

        // زر "ابحث"
        btnSearch.setOnClickListener { startSearch(input.text.toString()) }

        // IME action + ENTER
        input.setOnEditorActionListener { _, actionId, event ->
            val isImeSearch = actionId == EditorInfo.IME_ACTION_SEARCH
            val isEnterKey = event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                    event.action == KeyEvent.ACTION_DOWN
            if (isImeSearch || isEnterKey) { startSearch(input.text.toString()); true } else false
        }

        // بحث تلقائي بعد التوقف عن الكتابة
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                debounce(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // تحميل البيانات وبناء الفهرس بالخلفية
        progress.visibility = View.VISIBLE
        Thread {
            try {
                val ayat = SearchUtils.loadAllAyat(this)  // AyahItem(text, norm, surah, ayah, page)
                surahNames = SearchUtils.loadSurahNames(this)
                index = SearchIndex(ayat)
                indexReady = true
                runOnUiThread {
                    progress.visibility = View.GONE
                    hideCounter()
                    // ملاحظة: لا يوجد emptyView؛ التلميح داخل المربع فقط
                }
            } catch (_: Exception) {
                runOnUiThread {
                    progress.visibility = View.GONE
                    hideCounter()
                    Toast.makeText(this, "تعذّر تحميل البيانات", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    // ----- Debounce -----
    private fun debounce(q: String) {
        debounceRunnable?.let { handler.removeCallbacks(it) }
        debounceRunnable = Runnable { startSearch(q) }
        handler.postDelayed(debounceRunnable!!, 180) // أسرع
    }

    // ----- تنفيذ البحث -----
    private fun startSearch(queryRaw: String) {
        val q = queryRaw.trim()

        // إخفاء العداد مبدئيًا
        hideCounter()

        if (q.length < 2) {
            adapter.submitListWithQuery(emptyList(), "")
            return
        }
        if (!indexReady) {
            Toast.makeText(this, "جاري التجهيز…", Toast.LENGTH_SHORT).show()
            return
        }

        progress.visibility = View.VISIBLE
        val mySeq = seq.incrementAndGet()

        bg.submit {
            // (1) نتائج العرض — سريعة على norm داخل SearchIndex
            val hits = index.searchPartial(q)

            // (2) إحصائية على النتائج فقط
            val normQ = SearchUtils.normalizeArabic(SearchUtils.stripTashkeel(q))
            var totalOccurrences = 0
            val surahSet = HashSet<Int>()
            for (h in hits) {
                val normText = h.norm
                totalOccurrences += countOccurrences(normText, normQ)
                surahSet.add(h.surah)
            }
            val ayahMatches = hits.size
            val surahCount = surahSet.size

            if (mySeq != seq.get()) return@submit

            runOnUiThread {
                progress.visibility = View.GONE

                if (ayahMatches > 0) {
                    val msg = "وردت \"$q\" ${toArabic(totalOccurrences)} مرة في " +
                            "${toArabic(ayahMatches)} آية ضمن ${toArabic(surahCount)} سورة"
                    showCounter(msg)
                } else {
                    hideCounter()
                    Toast.makeText(this, "لا نتائج لـ \"$q\"", Toast.LENGTH_SHORT).show()
                }

                val uiList = hits.map { a ->
                    val name = surahNames.getOrNull(a.surah - 1) ?: "سورة ${a.surah}"
                    SearchResultItem(
                        surah = a.surah,
                        ayah = a.ayah,
                        page = a.page,
                        surahName = name,
                        text = a.text
                    )
                }
                adapter.setExactTashkeel(false)
                adapter.submitListWithQuery(uiList, q)
                list.scheduleLayoutAnimation()

                // تحديث سجلّ البحث إن كان الحقل يقبل اقتراحات
                (input as? AutoCompleteTextView)?.let { ac ->
                    SearchHistory.save(this, q)
                    val newList = SearchHistory.load(this)
                    suggestionsAdapter?.clear()
                    suggestionsAdapter?.addAll(newList)
                    suggestionsAdapter?.notifyDataSetChanged()
                    ac.post { if (ac.text.isNotEmpty()) ac.showDropDown() }
                }

                // في حال لم تكن بطاقة العداد موجودة لأي سبب
                if (counterText == null && ayahMatches > 0) {
                    Toast.makeText(
                        this,
                        "عدد النتائج: ${toArabic(ayahMatches)}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    /** عدّ ظهور pattern في text (بدون تداخل). النصان مُطبّعان مسبقًا. */
    private fun countOccurrences(text: String, pattern: String): Int {
        if (pattern.isEmpty() || text.isEmpty()) return 0
        var i = 0
        var c = 0
        while (true) {
            val p = text.indexOf(pattern, i)
            if (p < 0) break
            c++
            i = p + pattern.length
        }
        return c
    }

    // ----- عدّاد النتائج -----
    private fun showCounter(text: String) {
        counterText?.apply {
            this.text = text
            if (visibility != View.VISIBLE) {
                alpha = 0f
                visibility = View.VISIBLE
                counterCard?.visibility = View.VISIBLE
                animate().alpha(1f).setDuration(120).start()
            } else {
                counterCard?.visibility = View.VISIBLE
            }
        }
    }

    private fun hideCounter() {
        val tv = counterText ?: return
        tv.animate()
            .alpha(0f)
            .setDuration(100)
            .withEndAction {
                tv.visibility = View.GONE
                tv.alpha = 1f
                counterCard?.visibility = View.GONE
            }.start()
    }

    // ----- فتح الصفحة عند النقر -----
    override fun onResultClick(item: SearchResultItem) {
        val query = input.text?.toString()?.trim().orEmpty()
        val pageFromLocator = AyahLocator.getPageFor(this, item.surah, item.ayah)
        val finalPage = if (pageFromLocator > 0) pageFromLocator else item.page

        val intent = android.content.Intent(this, QuranPageActivity::class.java).apply {
            putExtra(QuranPageActivity.EXTRA_TARGET_SURAH, item.surah)
            putExtra(QuranPageActivity.EXTRA_TARGET_AYAH,  item.ayah)
            putExtra(QuranPageActivity.EXTRA_QUERY,        query)
            if (finalPage > 0) putExtra(QuranPageActivity.EXTRA_TARGET_PAGE, finalPage)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        debounceRunnable?.let { handler.removeCallbacks(it) }
        bg.shutdownNow()
    }

    private fun toArabic(n: Int): String {
        val d = charArrayOf('٠','١','٢','٣','٤','٥','٦','٧','٨','٩')
        return n.toString().map { d[it - '0'] }.joinToString("")
    }
}
