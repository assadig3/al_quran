package com.hag.al_quran

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.text.TextUtils
import android.view.*
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.MaterialToolbar
import com.hag.al_quran.audio.MadaniPageProvider
import com.hag.al_quran.helpers.QuranAudioHelper
import com.hag.al_quran.helpers.QuranSupportHelper
import com.hag.al_quran.search.AyahLocator
import com.hag.al_quran.ui.PageImageLoader
import com.hag.al_quran.utils.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.roundToLong

class QuranPageActivity : BaseActivity(), CenterLoaderHost {

    companion object {
        const val EXTRA_TARGET_SURAH = "EXTRA_TARGET_SURAH"
        const val EXTRA_TARGET_AYAH  = "EXTRA_TARGET_AYAH"
        const val EXTRA_TARGET_PAGE  = "EXTRA_TARGET_PAGE"
        const val EXTRA_QUERY        = "EXTRA_QUERY"

        private const val KEY_PAGES_CACHED = "pages_cached"
        private const val TOTAL_PAGES = 604

        // إشعار
        const val CHANNEL_ID = "quran_playback_channel"
        private const val NOTIF_ID   = 99111

        // أوامر الإشعار
        private const val ACT_TOGGLE = "com.hag.al_quran.NOTIF_TOGGLE"
        private const val ACT_STOP   = "com.hag.al_quran.NOTIF_STOP"

        // إذن إشعارات Android 13+
        private const val REQ_POST_NOTIFS = 8807
    }

    // UI
    lateinit var toolbar: MaterialToolbar
    lateinit var toolbarSpacer: View
    lateinit var viewPager: ViewPager2

    // شريط التلاوة
    lateinit var audioControls: LinearLayout
    lateinit var btnPlayPause: ImageButton
    lateinit var btnQari: TextView
    lateinit var audioDownload: ImageButton

    // صف التحميل في شريط التلاوة (إن وُجد)
    lateinit var downloadRow: LinearLayout
    lateinit var downloadProgress: ProgressBar
    lateinit var downloadLabel: TextView

    // شريط خيارات الآية
    lateinit var ayahOptionsBar: com.google.android.material.card.MaterialCardView
    lateinit var btnTafsir: ImageButton
    lateinit var btnShareAyah: ImageButton
    lateinit var btnCopyAyah: ImageButton
    lateinit var btnPlayAyah: ImageButton
    lateinit var btnCloseAyahBar: ImageButton
    var ayahPreview: TextView? = null

    // بانر “الآن يتلى”
    var ayahBanner: View? = null
    var ayahTextView: TextView? = null
    var ayahBannerSurah: TextView? = null
    var ayahBannerNumber: TextView? = null

    // شريط التحميل الوسطي (Overlay) — إن وُجد في Layout
    private lateinit var centerLoader: View
    private lateinit var centerLoaderText: TextView
    private lateinit var centerProgress: ProgressBar
    private lateinit var centerCount: TextView
    private lateinit var centerPercent: TextView
    private lateinit var centerEta: TextView
    private lateinit var btnPause: Button
    private lateinit var btnResume: Button
    private lateinit var btnClose: Button

    // خدمات
    lateinit var prefs: SharedPreferences
    lateinit var provider: MadaniPageProvider
    lateinit var audioHelper: QuranAudioHelper
    lateinit var supportHelper: QuranSupportHelper

    // حالة
    var currentQariId: String = "fares"
    var currentPage = 1
    var currentSurah = 1
    var currentAyah = 1

    // إخفاء/إظهار تلقائي
    var hideHandler: Handler? = null
    var hideRunnable: Runnable? = null

    // عرض الصفحات
    lateinit var adapter: AssetPageAdapter
    var lastPos: Int = -1

    // خلفية للصوت
    private val audioBgThread = HandlerThread("quran-audio-bg").apply { start() }
    internal val audioBgHandler by lazy { Handler(audioBgThread.looper) }
    private val uiHandler by lazy { Handler(Looper.getMainLooper()) }
    private var prepareQueueRunnable: Runnable? = null

    // تحكّم التحميل (اختياري)
    @Volatile private var isPaused = false
    @Volatile private var isCancelled = false
    @Volatile private var userClosedOverlay = false
    private val pauseLock = Object()
    private var exec: ExecutorService? = null

    @Volatile private var bulkPrefetchRunning = false
    private var centerVisibleLocks = 0

    // ===== مستقبل أوامر الإشعار (بدون ملف خارجي) =====
    private val notifReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACT_TOGGLE -> {
                    if (audioHelper.isPlaying) {
                        audioHelper.pausePagePlayback()
                        updateNotification(isPlaying = false)
                    } else {
                        audioHelper.startPagePlayback(currentPage, currentQariId)
                        updateNotification(isPlaying = true)
                    }
                }
                ACT_STOP -> {
                    audioHelper.pausePagePlayback()
                    NotificationManagerCompat.from(this@QuranPageActivity).cancel(NOTIF_ID)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntentAction(intent.action)
    }

    // === PendingIntent كبث داخلي للتطبيق (Broadcast) ===
    private fun pendingSelfBroadcast(action: String, reqCode: Int): PendingIntent {
        val i = Intent(this, com.hag.al_quran.notify.NotificationActionReceiver::class.java)
            .setAction(action)
        return PendingIntent.getBroadcast(
            this, reqCode, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    // === تنفيذ أوامر الأزرار القادمة من الإشعار أو onNewIntent ===
    private fun handleIntentAction(action: String?) {
        when (action) {
            ACT_TOGGLE -> {
                if (audioHelper.isPlaying) {
                    audioHelper.pausePagePlayback()
                    updateNotification(isPlaying = false)
                } else {
                    audioHelper.startPagePlayback(currentPage, currentQariId)
                    updateNotification(isPlaying = true)
                }
            }
            ACT_STOP -> {
                audioHelper.pausePagePlayback()
                NotificationManagerCompat.from(this).cancel(NOTIF_ID)
            }
        }
    }

    // ====== دورة الحياة ======
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quran_page)

        // قناة الإشعارات + إذن Android 13+
        ensureNotificationChannel()
        requestNotifPermissionIfNeeded()

        // خدمات
        prefs = getSharedPreferences("quran_prefs", Context.MODE_PRIVATE)
        provider = MadaniPageProvider(this)
        supportHelper = QuranSupportHelper(this, provider)
        audioHelper = QuranAudioHelper(this, provider, supportHelper, audioBgHandler)

        // قراءة الـ extras
        val pageFromNew = intent.getIntExtra(EXTRA_TARGET_PAGE, 0)
        val pageFromOld = intent.getIntExtra("page", intent.getIntExtra("page_number", 0))
        val surahFromNew = intent.getIntExtra(EXTRA_TARGET_SURAH, 0)
        val ayahFromNew = intent.getIntExtra(EXTRA_TARGET_AYAH, 0)
        val surahFromOld = intent.getIntExtra("surah_number", 0)
        val ayahFromOld = intent.getIntExtra("ayah_number", 0)

        currentSurah = if (surahFromNew > 0) surahFromNew else if (surahFromOld > 0) surahFromOld else 1
        currentAyah  = if (ayahFromNew  > 0) ayahFromNew  else if (ayahFromOld  > 0) ayahFromOld  else 1

        currentPage = when {
            pageFromNew > 0 -> pageFromNew
            pageFromOld > 0 -> pageFromOld
            else -> try { AyahLocator.getPageFor(this, currentSurah, currentAyah) } catch (_: Throwable) { 1 }
        }.coerceIn(1, TOTAL_PAGES)

        // Toolbar
        toolbar = findViewById(R.id.toolbar)
        toolbarSpacer = findViewById(R.id.toolbarSpacer)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
        supportActionBar?.title =
            supportHelper.getSurahNameForPage(currentPage).ifEmpty { getString(R.string.app_name) }

        // ViewPager
        viewPager = findViewById(R.id.pageViewPager)
        viewPager.offscreenPageLimit = 1
        (viewPager.getChildAt(0) as? RecyclerView)?.apply {
            itemAnimator = null
            setHasFixedSize(true)
            setItemViewCacheSize(4)
        }

        // شريط التلاوة
        audioControls  = findViewById(R.id.audioControls)
        btnPlayPause   = findViewById(R.id.btnPlayPause)
        btnQari        = findViewById(R.id.btnQari)
        audioDownload  = findViewById(R.id.audio_download)
        downloadRow     = findViewById(R.id.downloadRow)
        downloadProgress= findViewById(R.id.downloadProgress)
        downloadLabel   = findViewById(R.id.downloadLabel)

        // شريط الخيارات
        ayahOptionsBar   = findViewById(R.id.ayahOptionsBar)
        btnTafsir        = findViewById(R.id.btnTafsir)
        btnShareAyah     = findViewById(R.id.btnShareAyah)
        btnCopyAyah      = findViewById(R.id.btnCopyAyah)
        btnPlayAyah      = findViewById(R.id.btnPlayAyah)
        btnCloseAyahBar  = findViewById(R.id.btnClose)
        ayahPreview      = findViewById(R.id.ayahPreview)
        initMarquee(ayahPreview)
        ayahOptionsBar.visibility = View.GONE

        // بانر الآن يتلى
        val root = findViewById<ViewGroup>(android.R.id.content)
        val banner = layoutInflater.inflate(R.layout.ayah_now_playing, root, false)
        ayahBanner        = banner
        ayahTextView      = banner.findViewById(R.id.ayahText)
        ayahBannerSurah   = banner.findViewById(R.id.surahName)
        ayahBannerNumber  = banner.findViewById(R.id.ayahNumber)
        initMarquee(ayahTextView)
        banner.findViewById<ImageButton>(R.id.btnCloseBanner)
            .setOnClickListener { supportHelper.hideAyahBanner() }
        banner.visibility = View.GONE
        root.addView(banner)

        // Overlay (إن وُجد في Layout)
        val container: ViewGroup = findViewById(R.id.quran_container)
        val overlay = layoutInflater.inflate(R.layout.view_center_loader, container, false)
        container.addView(overlay)
        overlay.bringToFront()
        centerLoader     = overlay
        centerLoaderText = overlay.findViewById(R.id.centerText)
        centerProgress   = overlay.findViewById(R.id.centerProgress)
        centerCount      = overlay.findViewById(R.id.centerCount)
        centerPercent    = overlay.findViewById(R.id.centerPercent)
        centerEta        = overlay.findViewById(R.id.centerEta)
        btnPause         = overlay.findViewById(R.id.btnPause)
        btnResume        = overlay.findViewById(R.id.btnResume)
        btnClose         = overlay.findViewById(R.id.btnClose)
        centerLoader.visibility = View.GONE
        centerLoaderText.isSelected = true

        // تحكم التحميل
        btnPause.setOnClickListener {
            isPaused = true
            btnPause.isEnabled = false
            btnResume.isEnabled = true
        }
        btnResume.setOnClickListener {
            isPaused = false
            synchronized(pauseLock) { pauseLock.notifyAll() }
            btnPause.isEnabled = true
            btnResume.isEnabled = false
        }
        btnClose.setOnClickListener {
            userClosedOverlay = true
            centerVisibleLocks = 0
            centerLoader.visibility = View.GONE
            Toast.makeText(this, "سيستمر التنزيل في الخلفية.", Toast.LENGTH_SHORT).show()
        }

        // أزرار شريط الآية
        btnPlayAyah.setOnClickListener {
            if (audioHelper.isAyahPlaying) {
                audioHelper.stopSingleAyah()
                updateNotification(isPlaying = false)
            } else {
                audioHelper.playSingleAyah(currentSurah, currentAyah, currentQariId)
                updateNotification(
                    isPlaying = true,
                    surah = currentSurah,
                    ayah  = currentAyah,
                    customText = ayahPreview?.text?.toString()
                )
            }
            supportHelper.showToolbarAndHideAfterDelay()
        }
        btnCopyAyah.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = ayahPreview?.text?.toString().orEmpty()
            cm.setPrimaryClip(ClipData.newPlainText("Ayah", text))
            Toast.makeText(this, "تم نسخ الآية!", Toast.LENGTH_SHORT).show()
            supportHelper.showToolbarAndHideAfterDelay()
        }
        btnTafsir.setOnClickListener { supportHelper.openTafsir(currentSurah, currentAyah) }
        btnShareAyah.setOnClickListener { supportHelper.shareCurrentAyah(currentSurah, currentAyah) }
        btnCloseAyahBar.setOnClickListener { ayahOptionsBar.visibility = View.GONE }

        // اختيار القارئ
        btnQari.text = provider.getQariById(currentQariId)?.name ?: "فارس عباد"
        btnQari.setOnClickListener {
            supportHelper.showQariPicker { qari ->
                currentQariId = qari.id
                btnQari.text = qari.name
                supportHelper.showToolbarAndHideAfterDelay()
                debouncePrepareQueue(currentPage)
            }
        }

        audioDownload.setOnClickListener {
            supportHelper.showDownloadScopeDialog(currentPage, currentSurah, currentQariId)
        }

        btnPlayPause.setOnClickListener {
            supportHelper.showToolbarAndHideAfterDelay()
            if (audioHelper.isPlaying) {
                audioHelper.pausePagePlayback()
                updateNotification(isPlaying = false)
            } else {
                audioHelper.startPagePlayback(currentPage, currentQariId)
                updateNotification(isPlaying = true)
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(audioControls) { v, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            v.updatePadding(bottom = bottom + 16)
            insets
        }

        // Adapter
        val pageNames = (1..TOTAL_PAGES).map { "page_$it.webp" }
        adapter = AssetPageAdapter(
            context = this,
            pages = pageNames,
            realPageNumber = 0,
            onAyahClick = { s, a, t ->
                currentSurah = s
                currentAyah  = a
                supportHelper.showAyahOptionsBar(s, a, t)
                supportHelper.showToolbarAndHideAfterDelay()
            },
            onImageTap = { supportHelper.showToolbarAndHideAfterDelay() },
            onNeedPagesDownload = { },
            loaderHost = this
        )
        viewPager.adapter = adapter
        viewPager.setCurrentItem((currentPage - 1).coerceIn(0, TOTAL_PAGES - 1), false)

        viewPager.post { adapter.highlightAyahOnPage(currentPage, currentSurah, currentAyah) }
        PageImageLoader.prefetchAround(this, currentPage, radius = 1)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                if (lastPos != -1 && lastPos != position) {
                    adapter.clearHighlightOnPage(lastPos + 1)
                }
                lastPos = position
                currentPage = position + 1

                adapter.clearHighlightOnPage(currentPage)
                ayahOptionsBar.visibility = View.GONE

                val title = supportHelper.getSurahNameForPage(currentPage)
                    .ifEmpty { getString(R.string.app_name) }
                if (supportActionBar?.title != title) supportActionBar?.title = title

                saveLastVisitedPage(this@QuranPageActivity, currentPage)
                invalidateOptionsMenu()
                PageImageLoader.prefetchAround(this@QuranPageActivity, currentPage, radius = 1)
                debouncePrepareQueue(currentPage)
                supportHelper.showToolbarAndHideAfterDelay()
            }
        })

        // إخفاء/إظهار تلقائي
        hideHandler = Handler(Looper.getMainLooper())
        hideRunnable = Runnable { supportHelper.hideToolbarAndBottomBar() }
        supportHelper.showToolbarAndHideAfterDelay()

        // تحضير قائمة التلاوة
        debouncePrepareQueue(currentPage, immediate = true)

        // (اختياري) تنزيل الصفحات دفعة واحدة
        if (!arePagesCached()) startBulkPagesPrefetch(parallelism = 6)
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(ACT_TOGGLE)
            addAction(ACT_STOP)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                notifReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED // داخلي للتطبيق فقط
            )
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(notifReceiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        try { unregisterReceiver(notifReceiver) } catch (_: Exception) {}
    }

    // ===== إشعار التشغيل =====
    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "تشغيل التلاوة",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "إشعار تشغيل/إيقاف تلاوة القرآن" }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
    }

    private fun requestNotifPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_POST_NOTIFS
                )
            }
        }
    }

    // نجعلها عامة ليستطيع QuranAudioHelper استدعاءها
    fun updateNotification(
        isPlaying: Boolean,
        surah: Int? = null,
        ayah: Int? = null,
        customText: String? = null
    ) {
        ensureNotificationChannel()

        val title = if (surah != null && ayah != null) {
            val sName = supportHelper.getSurahNameByNumber(surah).ifEmpty { "سورة $surah" }
            "$sName • آية $ayah"
        } else {
            if (isPlaying) "جاري تلاوة القرآن" else "التلاوة متوقفة"
        }

        val text = when {
            !customText.isNullOrBlank() -> customText
            surah != null && ayah != null -> try {
                supportHelper.getAyahTextFromJson(surah, ayah)
            } catch (_: Throwable) { "—" }
            else -> "—"
        }

        val contentPI = PendingIntent.getActivity(
            this, 100,
            Intent(this, QuranPageActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(if (isPlaying) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)
            .setContentIntent(contentPI)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "إيقاف مؤقت" else "تشغيل",
                pendingSelfBroadcast(ACT_TOGGLE, 201)
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "إيقاف",
                pendingSelfBroadcast(ACT_STOP, 202)
            )

        val canPost = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        if (canPost) NotificationManagerCompat.from(this).notify(NOTIF_ID, builder.build())
    }

    // ===== باقي القوائم =====
    private fun debouncePrepareQueue(page: Int, immediate: Boolean = false) {
        prepareQueueRunnable?.let { uiHandler.removeCallbacks(it) }
        val r = Runnable { audioHelper.prepareAudioQueueForPage(page, currentQariId) }
        prepareQueueRunnable = r
        uiHandler.postDelayed(r, if (immediate) 0 else 120)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_page_viewer, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val item = menu.findItem(R.id.action_toggle_page_bookmark)
        item?.setIcon(
            if (isFavoritePage(this, currentPage)) R.drawable.ic_star_filled
            else R.drawable.ic_star_border
        )
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_toggle_page_bookmark -> {
                if (isFavoritePage(this, currentPage)) {
                    removeFavoritePage(this, currentPage)
                    item.setIcon(R.drawable.ic_star_border)
                    Toast.makeText(this, "تم إزالة حفظ الصفحة", Toast.LENGTH_SHORT).show()
                } else {
                    addFavoritePage(this, currentPage)
                    item.setIcon(R.drawable.ic_star_filled)
                    toolbar.startAnimation(AnimationUtils.loadAnimation(this, R.anim.star_click))
                    Toast.makeText(this, "تم حفظ الصفحة في المفضلة", Toast.LENGTH_SHORT).show()
                }
                true
            }
            R.id.action_download_tafsirs -> { supportHelper.showTafsirDownloadDialog(); true }
            R.id.action_toggle_repeat -> { supportHelper.showRepeatDialog(audioHelper); true }
            R.id.action_select_tafsir -> { supportHelper.showTafsirPickerDialog(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isCancelled = true
        exec?.shutdownNow()
        audioHelper.release()
        audioBgThread.quitSafely()
        hideRunnable?.let { hideHandler?.removeCallbacks(it) }
        // لا نلغي الإشعار هنا للحفاظ على التشغيل بالخلفية إن رغبت.
        // NotificationManagerCompat.from(this).cancel(NOTIF_ID)
    }

    private fun initMarquee(tv: TextView?) {
        tv?.apply {
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isFocusable = true
            isFocusableInTouchMode = true
            setHorizontallyScrolling(true)
            isSelected = true
        }
    }

    // ====== CenterLoaderHost ======
    override fun showCenterLoader(msg: String) { acquireCenterLock(msg) }
    fun showCenterLoader() { acquireCenterLock("جاري تنزيل صفحات المصحف…") }
    override fun hideCenterLoader() { releaseCenterLock() }

    private fun acquireCenterLock(msg: String? = null) {
        if (userClosedOverlay) return
        centerVisibleLocks++
        runOnUiThread {
            if (::centerLoaderText.isInitialized) msg?.let { centerLoaderText.text = it }
            if (::centerLoader.isInitialized) {
                centerLoader.visibility = View.VISIBLE
                centerLoader.bringToFront()
            }
        }
    }
    private fun releaseCenterLock() {
        if (centerVisibleLocks > 0) centerVisibleLocks--
        runOnUiThread {
            if (centerVisibleLocks == 0 && !bulkPrefetchRunning && ::centerLoader.isInitialized) {
                centerLoader.visibility = View.GONE
            }
        }
    }

    // ====== تنزيل الصفحات (اختياري) ======
    private fun arePagesCached(): Boolean = prefs.getBoolean(KEY_PAGES_CACHED, false)
    private fun setPagesCachedDone() { prefs.edit().putBoolean(KEY_PAGES_CACHED, true).apply() }

    private fun formatEta(sec: Long): String {
        val s = max(0, sec)
        val h = s / 3600
        val m = (s % 3600) / 60
        val ss = s % 60
        return if (h > 0) String.format("الوقت المتبقي: %d:%02d:%02d", h, m, ss)
        else String.format("الوقت المتبقي: %02d:%02d", m, ss)
    }

    private fun waitIfPaused() {
        synchronized(pauseLock) {
            while (isPaused && !isCancelled) {
                try { pauseLock.wait(150) } catch (_: InterruptedException) { break }
            }
        }
    }

    private fun startBulkPagesPrefetch(parallelism: Int = 6) {
        bulkPrefetchRunning = true
        isPaused = false
        isCancelled = false
        userClosedOverlay = false

        acquireCenterLock("جاري تنزيل صفحات المصحف…")
        centerProgress.isIndeterminate = false
        centerProgress.max = TOTAL_PAGES
        centerProgress.progress = 0
        centerCount.text = "0 / $TOTAL_PAGES"
        centerPercent.text = "  (0%)"
        centerEta.text = "الوقت المتبقي: …"
        btnPause.isEnabled = true
        btnResume.isEnabled = false

        val startMs = SystemClock.elapsedRealtime()
        val done = AtomicInteger(0)

        fun updateUI(c: Int) {
            centerProgress.progress = c
            centerCount.text = "$c / $TOTAL_PAGES"
            val pct = ((c * 100f) / TOTAL_PAGES.toFloat()).toInt().coerceIn(0, 100)
            centerPercent.text = "  (${pct}%)"

            val elapsedSec = max(1L, ((SystemClock.elapsedRealtime() - startMs) / 1000f).roundToLong())
            val rate = c.toFloat() / elapsedSec.toFloat()
            val remaining = (TOTAL_PAGES - c).coerceAtLeast(0)
            val etaSec = if (rate > 0f) (remaining / rate).roundToLong() else Long.MAX_VALUE
            centerEta.text = if (etaSec == Long.MAX_VALUE) "الوقت المتبقي: …" else formatEta(etaSec)
        }

        exec = Executors.newFixedThreadPool(parallelism).also { pool ->
            for (page in 1..TOTAL_PAGES) {
                pool.execute {
                    if (isCancelled) return@execute
                    waitIfPaused()
                    if (isCancelled) return@execute
                    try {
                        val model = if (page <= 3)
                            "file:///android_asset/pages/page_${page}.webp"
                        else
                            "https://cdn.jsdelivr.net/gh/assadig3/quran-pages@main/pages/page_${page}.webp"

                        com.bumptech.glide.Glide.with(this)
                            .downloadOnly()
                            .load(model)
                            .submit()
                            .get()
                    } catch (_: Exception) {
                    } finally {
                        val c = done.incrementAndGet()
                        runOnUiThread { updateUI(c) }
                    }
                }
            }

            Thread {
                pool.shutdown()
                try { pool.awaitTermination(45, TimeUnit.MINUTES) } catch (_: InterruptedException) {}
                runOnUiThread {
                    if (!isCancelled) {
                        setPagesCachedDone()
                        Toast.makeText(this, "اكتمل تنزيل صفحات المصحف", Toast.LENGTH_LONG).show()
                    }
                    bulkPrefetchRunning = false
                    userClosedOverlay = false
                    releaseCenterLock()
                }
            }.start()
        }
    }
}
