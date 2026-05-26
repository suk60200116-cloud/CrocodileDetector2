package com.smishing.crocodiledetector

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton

class SettingsOnboardingFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_ob_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 설정하기 버튼들
        view.findViewById<View>(R.id.btnOverlay).setOnClickListener {
            startActivity(Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${requireContext().packageName}")
            ))
        }

        view.findViewById<View>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        view.findViewById<View>(R.id.btnBattery).setOnClickListener {
            startActivity(Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${requireContext().packageName}")
            ))
        }

        // 다음 버튼 초기 비활성화
        updateNextButton()
    }

    override fun onResume() {
        super.onResume()
        // 설정 화면에서 돌아올 때마다 상태 체크
        updateNextButton()
        updateDotStates()
    }

    // ── 오버레이 권한 확인 ───────────────────────────────────────────────────
    private fun isOverlayGranted(): Boolean =
        Settings.canDrawOverlays(requireContext())

    // ── 접근성 서비스 확인 ───────────────────────────────────────────────────
    private fun isAccessibilityGranted(): Boolean {
        val enabledServices = Settings.Secure.getString(
            requireContext().contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(requireContext().packageName, ignoreCase = true)
    }

    // ── 다음 버튼 활성/비활성 ────────────────────────────────────────────────
    private fun updateNextButton() {
        val allGranted = isOverlayGranted() && isAccessibilityGranted()
        activity?.findViewById<MaterialButton>(R.id.btnNext)?.apply {
            isEnabled = allGranted
            alpha = if (allGranted) 1f else 0.4f
        }
    }

    // ── 타임라인 점 색상 업데이트 ────────────────────────────────────────────
    private fun updateDotStates() {
        val view = view ?: return

        // 오버레이 완료 표시
        updateItemState(
            view,
            dotId     = R.id.dotOverlay,
            labelId   = R.id.labelOverlay,
            btnId     = R.id.btnOverlay,
            isGranted = isOverlayGranted()
        )

        // 접근성 완료 표시
        updateItemState(
            view,
            dotId     = R.id.dotAccessibility,
            labelId   = R.id.labelAccessibility,
            btnId     = R.id.btnAccessibility,
            isGranted = isAccessibilityGranted()
        )
    }

    private fun updateItemState(
        view: View,
        dotId: Int,
        labelId: Int,
        btnId: Int,
        isGranted: Boolean
    ) {
        val dot   = view.findViewById<View>(dotId)
        val label = view.findViewById<TextView>(labelId)
        val btn   = view.findViewById<TextView>(btnId)

        if (isGranted) {
            dot.background   = resources.getDrawable(R.drawable.bg_tl_dot_active, null)
            label.alpha      = 1f
            btn.text         = "완료 ✓"
            btn.setTextColor(resources.getColor(android.R.color.white, null))
            btn.background   = resources.getDrawable(R.drawable.bg_pill_done, null)
            btn.isClickable  = false
        } else {
            dot.background   = resources.getDrawable(R.drawable.bg_tl_dot_dim, null)
            label.alpha      = 1f
            btn.text         = "설정하기"
            btn.setTextColor(resources.getColor(android.R.color.white, null))
            btn.background   = resources.getDrawable(R.drawable.bg_pill_outline, null)
            btn.isClickable  = true
        }
    }
}