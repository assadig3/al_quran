package com.hag.al_quran

import Juz
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class JuzAdapter(
    private val juzList: List<Juz>,
    private val onItemClick: (Juz) -> Unit
) : RecyclerView.Adapter<JuzAdapter.JuzViewHolder>() {

    inner class JuzViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val juzNumberText: TextView = itemView.findViewById(R.id.juzNumberText)
        val juzNameText: TextView = itemView.findViewById(R.id.juzNameText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JuzViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_juz, parent, false)
        return JuzViewHolder(view)
    }

    override fun onBindViewHolder(holder: JuzViewHolder, position: Int) {
        val juz = juzList[position]
        holder.juzNumberText.text = "الجزء ${juz.number}"
        holder.juzNameText.text = "يبدأ من الصفحة ${juz.startPage}"
        holder.itemView.setOnClickListener { onItemClick(juz) }
    }

    override fun getItemCount(): Int = juzList.size
}
