package com.hag.al_quran

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar

class QuranPageActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quran_page)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // قراءة اسم السورة أو اسم الجزء (أيهما متوفر)
        val surahName = intent.getStringExtra("surah_name")
        val juzName = intent.getStringExtra("juz_name")
        supportActionBar?.title = surahName ?: juzName ?: "القرآن الكريم"

        toolbar.setNavigationOnClickListener {
            finish()
        }

        val pageNumber = intent.getIntExtra("page_number", 1)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.quran_container, QuranPageFragment.newInstance(pageNumber))
                .commit()
        }
    }


}
