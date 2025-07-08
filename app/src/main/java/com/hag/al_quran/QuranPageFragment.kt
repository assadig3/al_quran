package com.hag.al_quran

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment

class QuranPageFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_quran_page, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val imageView = view.findViewById<ImageView>(R.id.quranPageImage)
        // اسم الصورة (جرّب أولاً page_1.webp)
        val assetFileName = "pages/page_1.webp"
        try {
            val inputStream = requireContext().assets.open(assetFileName)
            val drawable = Drawable.createFromStream(inputStream, null)
            imageView.setImageDrawable(drawable)
            inputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
