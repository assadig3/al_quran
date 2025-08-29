package com.hag.al_quran.helpers

import android.media.MediaPlayer
import android.os.Handler
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

    private val ayahQueue: MutableList<Triple<String, Int, Int>> = CopyOnWriteArrayList()

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
                activity.ayahOptionsBar.visibility = android.view.View.VISIBLE
                activity.adapter.highlightAyahOnPage(activity.currentPage, surah, ayah)

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
        }
    }

    fun startPagePlayback(page: Int, qariId: String) {
        isPlaying = false
        currentRepeat = 0; lastRepeatedAyah = null
        prepareAudioQueueForPage(page, qariId)
        activity.window.decorView.postDelayed({
            if (ayahQueue.isEmpty()) {
                Toast.makeText(activity, "لا توجد آيات على الصفحة الحالية.", Toast.LENGTH_SHORT).show()
            } else {
                playNextAyah(qariId)
            }
        }, 80)
    }

    fun pausePagePlayback() {
        try { mediaPlayer?.pause() } catch (_: Exception) {}
        isPlaying = false
        activity.btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
        // لا نفرّغ الطابور — يمكن الاستئناف من الإشعار
    }

    private fun playNextAyah(qariId: String) {
        if (ayahQueue.isEmpty()) {
            val next = activity.currentPage + 1
            if (next <= 604) {
                activity.viewPager.setCurrentItem(next - 1, true)
                // prepareAudioQueueForPage يُستدعى عند onPageSelected من الـActivity
                activity.window.decorView.postDelayed({
                    if (ayahQueue.isNotEmpty()) playNextAyah(qariId) else {
                        isPlaying = false
                        activity.btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
                        supportHelper.hideAyahBanner()
                    }
                }, 120)
            } else {
                isPlaying = false
                activity.btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
                supportHelper.hideAyahBanner()
            }
            return
        }

        val (url, s, a) = ayahQueue.removeAt(0)
        activity.currentSurah = s
        activity.currentAyah = a
        activity.adapter.highlightAyahOnPage(activity.currentPage, s, a)

        try {
            val local = qariFile(qariId, s, a)
            if (mediaPlayer == null) mediaPlayer = MediaPlayer() else mediaPlayer?.reset()
            if (local.exists()) mediaPlayer?.setDataSource(local.absolutePath) else mediaPlayer?.setDataSource(url)

            mediaPlayer?.setOnPreparedListener {
                it.start()
                isPlaying = true
                activity.btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)

                val ayahText = supportHelper.getAyahTextFromJson(s, a)
                supportHelper.showOrUpdateAyahBanner(s, a, ayahText)
                supportHelper.showAyahOptionsBar(s, a, ayahText)

                if (!local.exists()) supportHelper.downloadOneAsync("تحميل التلاوة…", url, local)
            }
            mediaPlayer?.setOnCompletionListener {
                if (repeatMode == "ayah") {
                    val key = s to a
                    if (lastRepeatedAyah == key) currentRepeat++ else { currentRepeat = 1; lastRepeatedAyah = key }
                    if (currentRepeat < repeatCount) {
                        ayahQueue.add(0, Triple(url, s, a))
                    } else {
                        currentRepeat = 0; lastRepeatedAyah = null
                    }
                }
                playNextAyah(qariId)
            }
            mediaPlayer?.prepareAsync()
        } catch (_: Exception) {
            Toast.makeText(activity, "تعذر تشغيل التلاوة", Toast.LENGTH_SHORT).show()
            playNextAyah(qariId)
        }
    }

    // ===== أدوات تخزين الملفات لكل قارئ =====
    private fun qariDir(qariId: String): File {
        val safe = qariId.lowercase().replace("\\s+".toRegex(), "_").replace("[^a-z0-9_\\-]".toRegex(), "")
        val dir = File(activity.getExternalFilesDir(null), "recitations/$safe")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun qariFile(qariId: String, surah: Int, ayah: Int): File {
        val name = "%03d%03d.mp3".format(surah, ayah)
        return File(qariDir(qariId), name)
    }
}
