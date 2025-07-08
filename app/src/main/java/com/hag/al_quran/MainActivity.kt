package com.hag.al_quran

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView

class MainActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_view)

        // إعداد زر القائمة الجانبية (الهامبرجر)
        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // تحميل الـ Fragment الأساسي
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_content, HomeTabsFragment())
                .commit()
        }

        // تعامل مع عناصر القائمة الجانبية
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.home -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.main_content, HomeTabsFragment())
                        .commit()
                }
                R.id.settings -> { /* افتح الإعدادات */ }
                R.id.help -> { /* افتح المساعدة */ }
                R.id.about -> { /* افتح عن التطبيق */ }
                R.id.share, R.id.share_qr, R.id.rate, R.id.other_apps -> { /* شارك/قيم */ }
                R.id.action_exit -> finish()
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    @SuppressLint("GestureBackNavigation")
    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}
