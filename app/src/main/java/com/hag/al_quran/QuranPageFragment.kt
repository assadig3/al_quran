package com.hag.al_quran

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2

class QuranPageFragment : Fragment() {
    private var hideHandler: Handler? = null
    private var hideRunnable: Runnable? = null

    companion object {
        private const val ARG_PAGE_NUMBER = "page_number"
        fun newInstance(pageNumber: Int): QuranPageFragment {
            val fragment = QuranPageFragment()
            val args = Bundle()
            args.putInt(ARG_PAGE_NUMBER, pageNumber)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_quran_page, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewPager = view.findViewById<ViewPager2>(R.id.pageViewPager)
        val pageNumbers = (1..604).toList()

        hideHandler = Handler(Looper.getMainLooper())
        hideRunnable = Runnable { hideToolbar() }

        // Adapter مع كول باك
        val adapter = QuranPagesAdapter(requireContext(), pageNumbers) {
            showToolbarAndHideAfterDelay()
        }
        viewPager.adapter = adapter

        val startPage = arguments?.getInt(ARG_PAGE_NUMBER) ?: 1
        viewPager.setCurrentItem(startPage - 1, false)

        // عند تغيير الصفحة
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                showToolbarAndHideAfterDelay()
            }
        })

        // أول مرة عند فتح الصفحة
        showToolbarAndHideAfterDelay()
    }

    private fun showToolbarAndHideAfterDelay() {
        showToolbar()
        hideHandler?.removeCallbacks(hideRunnable!!)
        hideHandler?.postDelayed(hideRunnable!!, 3000)
    }

    private fun showToolbar() {
        val activity = requireActivity() as? AppCompatActivity
        val toolbar = activity?.findViewById<Toolbar>(R.id.toolbar)
        toolbar?.visibility = View.VISIBLE
        toolbar?.animate()?.translationY(0f)?.setDuration(200)?.start()
    }

    private fun hideToolbar() {
        val activity = requireActivity() as? AppCompatActivity
        val toolbar = activity?.findViewById<Toolbar>(R.id.toolbar)
        toolbar?.animate()?.translationY(-toolbar.height.toFloat())?.setDuration(200)
            ?.withEndAction { toolbar.visibility = View.GONE }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        hideHandler?.removeCallbacks(hideRunnable!!)
    }
}
