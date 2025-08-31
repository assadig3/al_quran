package com.hag.al_quran.helpers

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.view.View
import android.widget.Toast
import com.hag.al_quran.QuranPageActivity
import com.hag.al_quran.R
import com.hag.al_quran.audio.MadaniPageProvider
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

class QuranAudioHelper(
    private val activity: QuranPageActivity,
    private val provider: MadaniPageProvider,
    val supportHelper: QuranSupportHelper,
    private val bgHandler: Handler
) {
    var mediaPlayer: MediaPlayer? = null
    @Volatile var isPlaying = false
    @Volatile var isAyahPlaying = false

    // url, surah, ayah
    private val ayahQueue: MutableList<Triple<String, Int, Int>> = CopyOnWriteArrayList()

    // مؤشر التقدّم داخل الطابور + زمن الاستئناف داخل نفس الآية
    private var currentIndex: Int = -1
    private var resumeFromMs: Int = 0

    // الاستمرار التلقائي للصفحة التالية
    var autoContinueToNextPage: Boolean = true

    // حفظ آخر آية تم تشغيلها (للاسترجاع بعد إعادة فتح الصفحة)
    private val prefs by lazy { activity.getSharedPreferences("quran_audio", Context.MODE_PRIVATE) }
    private fun saveLastAyah(surah: Int, ayah: Int) {
        prefs.edit().putInt("last_surah", surah).putInt("last_ayah", ayah).apply()
    }
    private fun loadLastAyah(): Pair<Int, Int>? {
        val s = prefs.getInt("last_surah", -1)
        val a = prefs.getInt("last_ayah", -1)
        return if (s > 0 && a > 0) (s to a) else null
    }

    // إعدادات التكرار
    var repeatMode: String = "ayah"
    var repeatCount: Int = 1
    private var currentRepeat = 0
    private var lastRepeatedAyah: Pair<Int, Int>? = null

    fun release() {
        try { mediaPlayer?.stop() } catch (_: Exception) {}
        mediaPlayer?.release()
        mediaPlayer = null
        isPlaying = false
        isAyahPlaying = false
        // لا نمسح currentIndex ولا آخر آية محفوظة هنا
    }

    // ====== آية واحدة ======
    fun playSingleAyah(surah: Int, ayah: Int, qariId: String) {
        val qari = provider.getQariById(qariId) ?: return
        val localFile = qariFile(qari.id, surah, ayah)

        mediaPlayer?.release()
        mediaPlayer = MediaPlayer()
        try {
            if (localFile.exists()) mediaPlayer?.setDataSource(localFile.absolutePath)
            else mediaPlayer?.setDataSource(provider.getAyahUrl(qari, surah, ayah))

            mediaPlayer?.setOnPreparedListener {
                val text = supportHelper.getAyahTextFromJson(surah, ayah)
                supportHelper.showOrUpdateAyahBanner(surah, ayah, text)
                it.start()
                isAyahPlaying = true
                activity.btnPlayAyah.setImageResource(R.drawable.ic_pause)
                activity.ayahOptionsBar.visibility = View.VISIBLE
                activity.adapter.highlightAyahOnPage(activity.currentPage, surah, ayah)
                saveLastAyah(surah, ayah)

                if (!localFile.exists()) {
                    supportHelper.downloadOneAsync(
                        "تحميل الآية…",
                        provider.getAyahUrl(qari, surah, ayah),
                        localFile
                    )
                }
            }
            mediaPlayer?.setOnCompletionListener {
                isAyahPlaying = false
                activity.btnPlayAyah.setImageResource(R.drawable.ic_play)
                supportHelper.hideAyahBanner()
            }
            mediaPlayer?.prepareAsync()
        } catch (_: Exception) {
            Toast.makeText(activity, "تعذر تشغيل التلاوة", Toast.LENGTH_SHORT).show()
        }
    }
    // يستأنف التلاوة من نفس الموضع إن كانت متوقفة مؤقتًا.
// يرجع true لو فعلاً استأنف، و false لو ما قدر (مثلاً ما في MediaPlayer).
    fun resumePagePlayback(): Boolean {
        val mp = mediaPlayer ?: return false
        return try {
            if (!mp.isPlaying) {
                // لو كان عندنا موضع محفوظ، MediaPlayer حافظه بالفعل بعد pause()
                // (إنت كمان بتخزن resumeFromMs، لكن مش لازم نعمل seek هنا)
                mp.start()
                isPlaying = true
                isAyahPlaying = true
                activity.runOnUiThread {
                    activity.btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
                }
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun stopSingleAyah() {
        try { mediaPlayer?.stop() } catch (_: Exception) {}
        mediaPlayer?.release()
        mediaPlayer = null
        isAyahPlaying = false
        activity.btnPlayAyah.setImageResource(R.drawable.ic_play)
    }

    // ====== الصفحة (طابور) ======
    fun prepareAudioQueueForPage(page: Int, qariId: String) {
        bgHandler.post {
            val qari = provider.getQariById(qariId) ?: return@post
            val list = supportHelper.loadAyahBoundsForPage(page)
            ayahQueue.clear()
            for (b in list) {
                val url = provider.getAyahUrl(qari, b.sura_id, b.aya_id)
                ayahQueue.add(Triple(url, b.sura_id, b.aya_id))
            }
            // حدد المؤشر حسب آخر آية محفوظة إن كانت ضمن الصفحة، وإلا ابدأ من 0
            val saved = loadLastAyah()
            currentIndex = saved?.let { (s, a) ->
                ayahQueue.indexOfFirst { it.second == s && it.third == a }
            }?.takeIf { it >= 0 } ?: 0
            resumeFromMs = 0
        }
    }

    fun startPagePlayback(page: Int, qariId: String) {
        currentRepeat = 0; lastRepeatedAyah = null
        prepareAudioQueueForPage(page, qariId)

        activity.window.decorView.postDelayed({
            if (ayahQueue.isEmpty()) {
                Toast.makeText(activity, "لا توجد آيات على الصفحة الحالية.", Toast.LENGTH_SHORT).show()
            } else {
                if (currentIndex !in ayahQueue.indices) currentIndex = 0
                playAt(currentIndex, resumeFromMs, qariId)
            }
        }, 80)
    }

    fun pausePagePlayback() {
        mediaPlayer?.let {
            try {
                resumeFromMs = it.currentPosition
                it.pause()
            } catch (_: Exception) {}
        }
        isPlaying = false
        activity.btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
    }

    fun stopPagePlayback() {
        try { mediaPlayer?.stop() } catch (_: Exception) {}
        try { mediaPlayer?.reset() } catch (_: Exception) {}
        isPlaying = false
        isAyahPlaying = false
        resumeFromMs = 0
        activity.btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
        // لا نمسح ayahQueue ولا currentIndex
    }

    // ----- تشغيل عنصر معيّن من الطابور -----
    private fun playAt(index: Int, startMs: Int, qariId: String) {
        if (index !in ayahQueue.indices) {
            // انتهت الصفحة
            val next = activity.currentPage + 1
            if (autoContinueToNextPage && next <= 604) {
                // حضّر طابور الصفحة التالية واذهب لها ثم ابدأ من أول آية
                prepareAudioQueueForPage(next, qariId)
                activity.viewPager.setCurrentItem(next - 1, true)
                activity.window.decorView.postDelayed({
                    currentIndex = 0
                    resumeFromMs = 0
                    if (ayahQueue.isNotEmpty()) {
                        playAt(0, 0, qariId)
                    } else {
                        isPlaying = false
                        activity.btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
                        supportHelper.hideAyahBanner()
                    }
                }, 180)
            } else {
                isPlaying = false
                activity.btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
                supportHelper.hideAyahBanner()
            }
            return
        }

        val (url, s, a) = ayahQueue[index]
        activity.currentSurah = s
        activity.currentAyah = a
        activity.adapter.highlightAyahOnPage(activity.currentPage, s, a)

        try {
            val local = qariFile(qariId, s, a)
            if (mediaPlayer == null) mediaPlayer = MediaPlayer() else mediaPlayer?.reset()
            if (local.exists()) mediaPlayer?.setDataSource(local.absolutePath) else mediaPlayer?.setDataSource(url)

            mediaPlayer?.setOnPreparedListener {
                if (startMs > 0) it.seekTo(startMs)
                it.start()
                isPlaying = true
                isAyahPlaying = true
                activity.btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)

                val ayahText = supportHelper.getAyahTextFromJson(s, a)
                supportHelper.showOrUpdateAyahBanner(s, a, ayahText)
                supportHelper.showAyahOptionsBar(s, a, ayahText)
                saveLastAyah(s, a)

                if (!local.exists()) supportHelper.downloadOneAsync("تحميل التلاوة…", url, local)
            }
            mediaPlayer?.setOnCompletionListener {
                isAyahPlaying = false
                // منطق التكرار
                if (repeatMode == "ayah") {
                    val key = s to a
                    if (lastRepeatedAyah == key) currentRepeat++ else { currentRepeat = 1; lastRepeatedAyah = key }
                    if (currentRepeat < repeatCount) {
                        resumeFromMs = 0
                        playAt(currentIndex, 0, qariId)
                        return@setOnCompletionListener
                    } else {
                        currentRepeat = 0; lastRepeatedAyah = null
                    }
                }
                // إلى الآية التالية
                resumeFromMs = 0
                currentIndex += 1
                playAt(currentIndex, 0, qariId)
            }
            mediaPlayer?.prepareAsync()
        } catch (_: Exception) {
            Toast.makeText(activity, "تعذر تشغيل التلاوة", Toast.LENGTH_SHORT).show()
            // تجاوز الخطأ وتقدّم
            resumeFromMs = 0
            currentIndex += 1
            playAt(currentIndex, 0, qariId)
        }
    }

    // ===== أدوات تخزين الملفات لكل قارئ =====
    private fun qariDir(qariId: String): File {
        val safe = qariId.lowercase()
            .replace("\\s+".toRegex(), "_")
            .replace("[^a-z0-9_\\-]".toRegex(), "")
        val dir = File(activity.getExternalFilesDir(null), "recitations/$safe")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun qariFile(qariId: String, surah: Int, ayah: Int): File {
        val name = "%03d%03d.mp3".format(surah, ayah)
        return File(qariDir(qariId), name)
    }
}
