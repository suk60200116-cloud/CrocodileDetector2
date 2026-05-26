package com.smishing.crocodiledetector

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton

class OnboardingActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var btnNext: MaterialButton
    private lateinit var dotsContainer: LinearLayout // TabLayout 대신 사용

    companion object {
        const val PAGE_WELCOME      = 0
        const val PAGE_FEATURES     = 1
        const val PAGE_PERMISSIONS  = 2
        const val PAGE_TERMS        = 3
        const val PAGE_SETTINGS     = 4
        const val PAGE_READY        = 5
        const val TOTAL_PAGES       = 6
        const val REQ_PERMISSIONS   = 200
    }

    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.READ_CALL_LOG,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("crocodile_settings", Context.MODE_PRIVATE)
        if (prefs.getBoolean("onboarding_done", false)) {
            goToMain(); return
        }

        setContentView(R.layout.activity_onboarding)

        viewPager     = findViewById(R.id.viewPager)
        btnNext       = findViewById(R.id.btnNext)
        dotsContainer = findViewById(R.id.dotsContainer) // 변경됨

        viewPager.adapter = OnboardingPagerAdapter(this)
        viewPager.isUserInputEnabled = false

        // 커스텀 점 인디케이터 초기화 (TabLayoutMediator 대체)
        setupDots(TOTAL_PAGES)
        updateDots(0)

        btnNext.setOnClickListener { handleNextClick() }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(position) // 페이지 넘길 때 점 업데이트
                btnNext.text = when (position) {
                    PAGE_PERMISSIONS -> "권한 허용하기"
                    PAGE_READY       -> "시작하기"
                    else             -> "다음"
                }
            }
        })
    }

    private fun handleNextClick() {
        when (viewPager.currentItem) {
            PAGE_PERMISSIONS -> requestPermissionsAndNext()
            PAGE_READY -> {
                getSharedPreferences("crocodile_settings", Context.MODE_PRIVATE)
                    .edit().putBoolean("onboarding_done", true).apply()
                goToMain()
            }
            else -> viewPager.currentItem++
        }
    }

    private fun requestPermissionsAndNext() {
        val missing = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            viewPager.currentItem++
        } else {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ_PERMISSIONS)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMISSIONS) viewPager.currentItem++
    }

    fun goToNextPage() { viewPager.currentItem++ }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    // ── 커스텀 점 인디케이터 함수 ───────────────────────────────────────────
    private fun setupDots(count: Int) {
        dotsContainer.removeAllViews()
        repeat(count) {
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(8.dpToPx(), 8.dpToPx()).apply {
                    marginEnd = 6.dpToPx()
                }
                background = ContextCompat.getDrawable(this@OnboardingActivity, R.drawable.bg_tl_dot_dim)
            }
            dotsContainer.addView(dot)
        }
    }

    private fun updateDots(selected: Int) {
        for (i in 0 until dotsContainer.childCount) {
            val dot = dotsContainer.getChildAt(i)
            if (i == selected) {
                dot.layoutParams = LinearLayout.LayoutParams(20.dpToPx(), 8.dpToPx()).apply {
                    marginEnd = 6.dpToPx()
                }
                dot.background = ContextCompat.getDrawable(this, R.drawable.bg_tl_dot_active)
            } else {
                dot.layoutParams = LinearLayout.LayoutParams(8.dpToPx(), 8.dpToPx()).apply {
                    marginEnd = 6.dpToPx()
                }
                dot.background = ContextCompat.getDrawable(this, R.drawable.bg_tl_dot_dim)
            }
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}

class OnboardingPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount() = OnboardingActivity.TOTAL_PAGES
    override fun createFragment(position: Int): Fragment = when (position) {
        OnboardingActivity.PAGE_WELCOME     -> WelcomeFragment()
        OnboardingActivity.PAGE_FEATURES    -> FeaturesFragment()
        OnboardingActivity.PAGE_PERMISSIONS -> PermissionsFragment()
        OnboardingActivity.PAGE_TERMS       -> TermsFragment()
        OnboardingActivity.PAGE_SETTINGS    -> SettingsOnboardingFragment()
        OnboardingActivity.PAGE_READY       -> ReadyFragment()
        else -> WelcomeFragment()
    }
}