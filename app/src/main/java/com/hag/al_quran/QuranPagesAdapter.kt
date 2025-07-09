package com.hag.al_quran

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.github.chrisbanes.photoview.PhotoView

class QuranPagesAdapter(
    private val context: Context,
    private val pageNumbers: List<Int>,
    private val onImageClick: (() -> Unit)? = null
) : RecyclerView.Adapter<QuranPagesAdapter.PageViewHolder>() {

    inner class PageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val photoView: PhotoView = view.findViewById(R.id.pageImage)
        val ayahHighlight: View = view.findViewById(R.id.ayahHighlight)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_quran_page, parent, false)
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val pageNumber = pageNumbers[position]
        val assetFileName = "pages/page_${pageNumber}.webp"
        try {
            val inputStream = context.assets.open(assetFileName)
            val drawable = Drawable.createFromStream(inputStream, null)
            holder.photoView.setImageDrawable(drawable)
            inputStream.close()
        } catch (e: Exception) {
            holder.photoView.setImageDrawable(null)
        }
        holder.ayahHighlight.visibility = View.GONE

        // أرسل إشارة عند النقر فقط
        holder.photoView.setOnClickListener {
            onImageClick?.invoke()
        }
    }

    override fun getItemCount(): Int = pageNumbers.size
}
