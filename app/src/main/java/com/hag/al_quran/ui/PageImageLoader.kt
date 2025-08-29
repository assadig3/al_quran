package com.hag.al_quran.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.hag.al_quran.R
import com.hag.al_quran.AyahBoundsRepo

object PageImageLoader {

    // مرايا متعددة (مرتبة حسب الأفضلية)
    private val MIRRORS = listOf(
        "https://cdn.jsdelivr.net/gh/assadig3/quran-pages@main/pages",     // jsDelivr (HTTP/2)
        "https://cdn.statically.io/gh/assadig3/quran-pages/main/pages",    // Staticaly CDN
        "https://raw.githubusercontent.com/assadig3/quran-pages/main/pages" // GitHub Raw (احتياطي)
    )

    private fun localAssetFor(page: Int) = "file:///android_asset/pages/page_${page}.webp"

    // توزيع الطلبات على المرايا لتقليل ضغط المضيف الواحد + تجاوز حدود per-host
    private fun remoteUrlFor(page: Int): String {
        val mirror = MIRRORS[page % MIRRORS.size]
        return "$mirror/page_${page}.webp"
    }

    private fun modelFor(page: Int) =
        if (page <= 3) localAssetFor(page) else remoteUrlFor(page)

    enum class Mode { NORMAL, FAST_PREVIEW }

    fun load(context: Context, pageNumber: Int, into: ImageView) {
        load(context, pageNumber, into, Mode.NORMAL)
    }

    fun load(context: Context, pageNumber: Int, into: ImageView, mode: Mode) {
        loadWithCallbacks(context, pageNumber, into, mode, onStart = {}, onReady = {}, onFail = {})
    }

    fun loadWithCallbacks(
        context: Context,
        pageNumber: Int,
        into: ImageView,
        mode: Mode = Mode.NORMAL,
        onStart: () -> Unit,
        onReady: () -> Unit,
        onFail: () -> Unit
    ) {
        val model = modelFor(pageNumber)
        val opts = RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.DATA)
            .skipMemoryCache(false)
            .dontAnimate()
            .downsample(DownsampleStrategy.AT_MOST)
            .format(DecodeFormat.PREFER_RGB_565)
            .timeout(10_000)
            .placeholder(R.drawable.ic_placeholder)
            .error(R.drawable.ic_error)

        val req = Glide.with(context)
            .load(model)
            .apply(opts)
            .priority(Priority.HIGH)
            .transition(DrawableTransitionOptions.withCrossFade())
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean
                ): Boolean { onFail(); return false }
                override fun onResourceReady(
                    resource: Drawable, model: Any, target: Target<Drawable>?, dataSource: DataSource, isFirstResource: Boolean
                ): Boolean { onReady(); return false }
            })

        if (mode == Mode.FAST_PREVIEW) req.thumbnail(0.25f)

        onStart()
        req.into(into)
    }

    fun prefetchAround(context: Context, currentPage: Int, radius: Int = 2) {
        for (p in (currentPage - radius)..(currentPage + radius)) {
            if (p < 1 || p > 604) continue
            Glide.with(context)
                .load(modelFor(p))
                .diskCacheStrategy(DiskCacheStrategy.DATA)
                .skipMemoryCache(false)
                .preload()
        }
    }

    fun baseSizeFor(boundsList: List<AyahBoundsRepo>): Pair<Float, Float> {
        var maxX = 0f; var maxY = 0f
        for (ab in boundsList) for (s in ab.segs) {
            val ex = (s.x + s.w).toFloat()
            val ey = (s.y + s.h).toFloat()
            if (ex > maxX) maxX = ex
            if (ey > maxY) maxY = ey
        }
        if (maxX <= 0f || maxY <= 0f) return 1080f to 1650f
        return maxX to maxY
    }

    // للمحمّل الموازي في النشاط
    fun directUrlFor(page: Int): String = modelFor(page)
}
