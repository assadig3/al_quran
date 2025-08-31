package com.hag.al_quran.tafsir

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.hag.al_quran.R

/**
 * BottomSheet سريع لعرض التفسير.
 * يعتمد على layout: bottomsheet_fast_tafsir.xml
 * IDs المطلوبة في الـ XML: title, progress, tafsirText, btnClose
 */
class FastTafsirBottomSheet : BottomSheetDialogFragment() {

    private var surah: Int = 1
    private var ayah: Int  = 1

    private lateinit var titleTv: TextView
    private lateinit var tafsirTv: TextView
    private lateinit var progress: ProgressBar
    private lateinit var btnClose: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            surah = it.getInt(ARG_SURAH, 1)
            ayah  = it.getInt(ARG_AYAH, 1)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // غيّر الاسم هنا لو كان اسم ملفك مختلفًا
        return inflater.inflate(R.layout.bottomsheet_fast_tafsir, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // مطابق تمامًا للـ XML: title / progress / tafsirText / btnClose
        titleTv  = view.findViewById(R.id.title)
        tafsirTv = view.findViewById(R.id.tafsirText)
        progress = view.findViewById(R.id.progress)
        btnClose = view.findViewById(R.id.btnClose)

        titleTv.text = "التفسير - سورة $surah • آية $ayah"
        tafsirTv.text = "جارٍ التحميل…"
        progress.isVisible = true

        // جلب التفسير مع كاش داخلي عبر TafsirManager
        val manager = TafsirManager(requireContext())
        manager.fetchFromCDN(surah, ayah) { txt ->
            if (!isAdded) return@fetchFromCDN
            progress.isVisible = false
            tafsirTv.text = if (txt.isNotBlank()) txt else "لم يتم العثور على التفسير."
        }

        btnClose.setOnClickListener { dismiss() }
    }

    companion object {
        private const val ARG_SURAH = "arg_surah"
        private const val ARG_AYAH  = "arg_ayah"

        fun newInstance(surah: Int, ayah: Int) = FastTafsirBottomSheet().apply {
            arguments = Bundle().apply {
                putInt(ARG_SURAH, surah)
                putInt(ARG_AYAH, ayah)
            }
        }
    }
}
