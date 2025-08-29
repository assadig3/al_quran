package com.hag.al_quran.Surah

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hag.al_quran.R

/**
 * Adapter سريع باستخدام ListAdapter + DiffUtil
 * يعتمد تخطيط item_surah بمعرّفات:
 *  - R.id.surahTitle
 *  - R.id.surahNumber
 *  - R.id.surahDetails
 */
class SurahAdapter(
    private val onClick: (Surah) -> Unit
) : ListAdapter<Surah, SurahAdapter.SurahViewHolder>(DIFF) {

    init { setHasStableIds(true) }

    override fun getItemId(position: Int): Long = getItem(position).number.toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SurahViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_surah, parent, false)
        return SurahViewHolder(view, onClick)
    }

    override fun onBindViewHolder(holder: SurahViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /** للتوافق مع أي كود قديم */
    fun updateList(newList: List<Surah>) = submitList(newList)

    class SurahViewHolder(
        itemView: View,
        private val onClick: (Surah) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val nameText: TextView = itemView.findViewById(R.id.surahTitle)
        private val numberText: TextView = itemView.findViewById(R.id.surahNumber)
        private val infoText: TextView = itemView.findViewById(R.id.surahDetails)

        private var current: Surah? = null

        init {
            itemView.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) current?.let(onClick)
            }
        }

        fun bind(surah: Surah) {
            current = surah

            val locale = itemView.resources.configuration.locales[0]
            val isAr = locale.language == "ar"

            nameText.text   = if (isAr) surah.name else surah.englishName
            numberText.text = if (isAr) toArabicDigits(surah.number) else surah.number.toString()

            val typeText = if (isAr) {
                surah.type // "مكية"/"مدنية"
            } else {
                when (surah.type) { "مكية" -> "Meccan"; "مدنية" -> "Medinan"; else -> surah.type }
            }
            val versesLabel = if (isAr) "آيات" else "verses"
            val ayahCountStr = if (isAr) toArabicDigits(surah.ayahCount) else surah.ayahCount.toString()
            infoText.text = "$typeText • $ayahCountStr $versesLabel"
        }

        private fun toArabicDigits(n: Int): String {
            val map = charArrayOf('٠','١','٢','٣','٤','٥','٦','٧','٨','٩')
            return n.toString().map { map[it.digitToInt()] }.joinToString("")
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Surah>() {
            override fun areItemsTheSame(oldItem: Surah, newItem: Surah) =
                oldItem.number == newItem.number
            override fun areContentsTheSame(oldItem: Surah, newItem: Surah) =
                oldItem == newItem
        }
    }
}
