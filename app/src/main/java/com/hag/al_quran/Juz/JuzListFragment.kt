package com.hag.al_quran.Juz

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.*
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hag.al_quran.QuranPageActivity
import com.hag.al_quran.R
import com.hag.al_quran.Surah.SurahAdapter
import com.hag.al_quran.Surah.SurahUtils

class JuzListFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: JuzAdapter
    private lateinit var prefs: SharedPreferences
    private var juzList: List<Juz> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_juz, menu)
        val color = ContextCompat.getColor(requireContext(), R.color.menu_overflow_text)
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            val span = SpannableString(item.title)
            span.setSpan(ForegroundColorSpan(color), 0, span.length, 0)
            item.title = span
        }
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_jump_to_juz   -> { showJuzPickerDialog(); true }
            R.id.action_jump_to_surah -> { showSurahPickerDialog(); true }
            R.id.nav_last_read -> {
                val lastJuz = prefs.getInt("last_juz", 30)
                val page = getFirstPageOfJuz(lastJuz)
                startActivity(Intent(requireContext(), QuranPageActivity::class.java).apply {
                    putExtra("page_number", page)
                })
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun getFirstPageOfJuz(j: Int): Int {
        val p = intArrayOf(
            1,22,42,62,82,102,121,142,162,182,
            201,222,242,262,282,302,322,342,362,382,
            402,422,442,462,482,502,522,542,562,582
        )
        return p.getOrElse(j-1) { 582 }
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val v = inflater.inflate(R.layout.fragment_juz_list, container, false)

        prefs = requireContext().getSharedPreferences("quran_prefs", Context.MODE_PRIVATE)

        recyclerView = v.findViewById(R.id.juzRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.setHasFixedSize(true)
        recyclerView.itemAnimator = null

        // البيانات
        juzList = buildAllJuz()

        adapter = JuzAdapter { j ->
            prefs.edit().putInt("last_juz", j.number).apply()
            startActivity(Intent(requireContext(), QuranPageActivity::class.java).apply {
                putExtra("page_number", j.pageNumber)
            })
        }
        recyclerView.adapter = adapter
        adapter.submitList(juzList)

        // إن كنت أضفت حقل البحث في fragment_juz_list.xml
        val search: EditText? = v.findViewById(R.id.juzSearchField)
        search?.addTextChangedListener { e ->
            val q = e?.toString()?.trim().orEmpty()
            val locale = requireContext().resources.configuration.locales[0]
            val filtered = if (q.isEmpty()) juzList else {
                if (locale.language == "ar")
                    juzList.filter { it.name.contains(q) || toArabicDigits(it.number).contains(q) }
                else
                    juzList.filter { it.englishName.contains(q, true) || it.number.toString().contains(q) }
            }
            adapter.submitList(filtered)
        }

        return v
    }

    private fun buildAllJuz(): List<Juz> {
        val arabicNames = resources.getStringArray(R.array.juz_names) // أو أنشئها يدويًا
        val englishNames = (1..30).map { "Juz $it" }
        val startPages = listOf(
            1,22,42,62,82,102,121,142,162,182,
            201,222,242,262,282,302,322,342,362,382,
            402,422,442,462,482,502,522,542,562,582
        )
        return List(30) { i ->
            Juz(
                number = i+1,
                name = arabicNames.getOrNull(i) ?: "الجزء ${i+1}",
                englishName = englishNames[i],
                pageNumber = startPages[i]
            )
        }
    }

    private fun showJuzPickerDialog() {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_jump_juz, null)
        val recycler = view.findViewById<RecyclerView>(R.id.jumpJuzRecyclerView)
        val search   = view.findViewById<EditText>(R.id.juzSearchField)
        val cancel   = view.findViewById<TextView>(R.id.btnCancelJuzDialog)

        val dlg = AlertDialog.Builder(requireContext()).setView(view).create()

        val dialogAdapter = JuzAdapter { j ->
            prefs.edit().putInt("last_juz", j.number).apply()
            startActivity(Intent(requireContext(), QuranPageActivity::class.java).apply {
                putExtra("page_number", j.pageNumber)
            })
            dlg.dismiss()
        }

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.setHasFixedSize(true)
        recycler.itemAnimator = null
        recycler.adapter = dialogAdapter
        dialogAdapter.submitList(juzList)

        search.addTextChangedListener { e ->
            val q = e?.toString()?.trim().orEmpty()
            val locale = requireContext().resources.configuration.locales[0]
            val filtered = if (q.isEmpty()) juzList else {
                if (locale.language == "ar")
                    juzList.filter { it.name.contains(q) || toArabicDigits(it.number).contains(q) }
                else
                    juzList.filter { it.englishName.contains(q, true) || it.number.toString().contains(q) }
            }
            dialogAdapter.submitList(filtered)
        }

        cancel.setOnClickListener { dlg.dismiss() }
        dlg.show()
    }

    private fun showSurahPickerDialog() {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_jump_surah, null)
        val recycler = view.findViewById<RecyclerView>(R.id.jumpSurahRecyclerView)
        val search   = view.findViewById<EditText>(R.id.surahSearchField)
        val cancel   = view.findViewById<TextView>(R.id.btnCancelSurahDialog)

        val surahs = SurahUtils.getAllSurahs(requireContext())
        val surahAdapter = SurahAdapter { s ->
            startActivity(Intent(requireContext(), QuranPageActivity::class.java).apply {
                putExtra("page_number", s.pageNumber)
            })
            // لا نغلق الحوار هنا لو تحبَّ تجرّب أكثر من اختيار—أغلقه إن أردت
        }

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.setHasFixedSize(true)
        recycler.itemAnimator = null
        recycler.adapter = surahAdapter
        surahAdapter.submitList(surahs)

        search.addTextChangedListener { e ->
            val q = e?.toString()?.trim().orEmpty()
            val locale = requireContext().resources.configuration.locales[0]
            val filtered = if (q.isEmpty()) surahs else {
                if (locale.language == "ar") surahs.filter { it.name.contains(q) }
                else surahs.filter { it.englishName.contains(q, true) }
            }
            surahAdapter.submitList(filtered)
        }

        val dlg = AlertDialog.Builder(requireContext()).setView(view).create()
        cancel.setOnClickListener { dlg.dismiss() }
        dlg.show()
    }

    private fun toArabicDigits(n: Int): String {
        val map = charArrayOf('٠','١','٢','٣','٤','٥','٦','٧','٨','٩')
        return n.toString().map { map[it.digitToInt()] }.joinToString("")
    }
}
