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

    private lateinit var root: View
    private lateinit var input: EditText
    private lateinit var btnSearch: View
    private lateinit var progress: ProgressBar
    private lateinit var list: RecyclerView
    private lateinit var counterText: TextView
    private lateinit var counterCard: View

    private val adapter = SearchResultAdapter(this)
    private val handler = Handler(Looper.getMainLooper())
    private val bg = Executors.newSingleThreadExecutor()
    private val seq = AtomicInteger(0)
    private var debounceRunnable: Runnable? = null

    private lateinit var index: SearchIndex
    private lateinit var surahNames: List<String>
    private var indexReady = false

    private var suggestionsAdapter: ArrayAdapter<String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        root        = findViewById(R.id.searchRoot) ?: findViewById(android.R.id.content)
        input       = findViewById(R.id.searchInput)
        btnSearch   = findViewById(R.id.btnSearch)
        progress    = findViewById(R.id.progress)
        list        = findViewById(R.id.resultsList)
        counterText = findViewById(R.id.resultCounter)
        counterCard = findViewById(R.id.counterCard)

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(v.paddingLeft, top + 12, v.paddingRight, v.paddingBottom)
            insets
        }

        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        list.setHasFixedSize(true)
        list.itemAnimator = null

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

        btnSearch.setOnClickListener { startSearch(input.text.toString()) }

        input.setOnEditorActionListener { _, actionId, event ->
            val isImeSearch = actionId == EditorInfo.IME_ACTION_SEARCH
            val isEnterKey = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (isImeSearch || isEnterKey) { startSearch(input.text.toString()); true } else false
        }

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                debounce(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        progress.visibility = View.VISIBLE
        Thread {
            try {
                val ayat = SearchUtils.loadAllAyat(this)
                surahNames = SearchUtils.loadSurahNames(this)
                index = SearchIndex(ayat)
                indexReady = true
                runOnUiThread {
                    progress.visibility = View.GONE
                    hideCounter()
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

    private fun debounce(q: String) {
        debounceRunnable?.let { handler.removeCallbacks(it) }
        debounceRunnable = Runnable { startSearch(q) }
        handler.postDelayed(debounceRunnable!!, 180)
    }

    private fun startSearch(queryRaw: String) {
        val q = queryRaw.trim()
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
            val hits: List<SearchIndex.AyahItem> = if (q.contains(' ')) {
                val terms = q.split(' ').filter { it.isNotBlank() }
                val lists = terms.map { term ->
                    try { index.searchWholeWord(term) } catch (_: Throwable) { index.searchPartial(term) }
                }
                val idList = lists.map { lst -> lst.map { it.surah to it.ayah }.toSet() }
                val commonIds = idList.reduceOrNull { acc, s -> acc.intersect(s) } ?: emptySet()
                val joined = lists.flatten()
                    .distinctBy { it.surah to it.ayah }
                    .filter { (it.surah to it.ayah) in commonIds }
                if (joined.isNotEmpty()) joined else index.searchPartial(q)
            } else {
                val whole = try { index.searchWholeWord(q) } catch (_: Throwable) { emptyList() }
                if (whole.isNotEmpty()) whole else index.searchPartial(q)
            }

            // فلترة البسملة
            val normQAll = SearchUtils.normalizeArabic(SearchUtils.stripTashkeel(q))
            val hitsFinal = if (normQAll == "الرحمن" || normQAll == "الرحيم") {
                hits.filter { a ->
                    val n = SearchUtils.normalizeArabic(SearchUtils.stripTashkeel(a.text))
                    !(a.ayah == 1 && a.surah != 9 && n.startsWith("بسم الله"))
                }
            } else hits

            var totalOccurrences = 0
            val surahSet = HashSet<Int>()
            if (q.contains(' ')) {
                val normQ = SearchUtils.normalizeArabic(SearchUtils.stripTashkeel(q))
                for (h in hitsFinal) {
                    totalOccurrences += countPhraseInOriginal(h.text, normQ)
                    surahSet.add(h.surah)
                }
            } else {
                for (h in hitsFinal) {
                    totalOccurrences += SearchUtils.findWholeWordRanges(h.text, q).size
                    surahSet.add(h.surah)
                }
            }
            val ayahMatches = hitsFinal.size
            val surahCount = surahSet.size

            if (mySeq != seq.get()) return@submit

            runOnUiThread {
                progress.visibility = View.GONE

                if (ayahMatches > 0) {
                    val msg = if (q.contains(' ')) {
                        "وردت العبارة \"$q\" ${toArabic(totalOccurrences)} مرة في " +
                                "${toArabic(ayahMatches)} آية ضمن ${toArabic(surahCount)} سورة"
                    } else {
                        "وردت \"$q\" ${toArabic(totalOccurrences)} مرة في " +
                                "${toArabic(ayahMatches)} آية ضمن ${toArabic(surahCount)} سورة"
                    }
                    showCounter(msg)
                } else {
                    hideCounter()
                    Toast.makeText(this, "لا نتائج لـ \"$q\"", Toast.LENGTH_SHORT).show()
                }

                val uiList = hitsFinal.map { a ->
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
            }
        }
    }

    private fun countPhraseInOriginal(original: String, phraseNorm: String): Int {
        if (phraseNorm.isEmpty()) return 0
        val (nText, _) = SearchUtils.buildNormalizedWithIndexMap(original)
        if (nText.isEmpty()) return 0

        fun isAr(ch: Char): Boolean = ch in '\u0621'..'\u064A'

        var i = 0
        var c = 0
        val L = phraseNorm.length
        while (true) {
            val p = nText.indexOf(phraseNorm, i)
            if (p < 0) break
            val beforeOk = (p == 0) || !isAr(nText[p - 1])
            val afterPos = p + L
            val afterOk = (afterPos >= nText.length) || !isAr(nText[afterPos])
            if (beforeOk && afterOk) c++
            i = p + L
        }
        return c
    }

    private fun showCounter(text: String) {
        counterText.text = text
        counterText.visibility = View.VISIBLE
        counterCard.visibility = View.VISIBLE
    }

    private fun hideCounter() {
        counterCard.visibility = View.GONE
        counterText.visibility = View.GONE
    }

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
