package com.smishing.crocodiledetector

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton

class TermsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_ob_terms, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val cbAll      = view.findViewById<CheckBox>(R.id.cbAll)
        val cbService  = view.findViewById<CheckBox>(R.id.cbService)
        val cbPrivacy  = view.findViewById<CheckBox>(R.id.cbPrivacy)
        val cbMarketing = view.findViewById<CheckBox>(R.id.cbMarketing)
        val btnNext    = requireActivity().findViewById<MaterialButton>(R.id.btnNext)

        val btnDetailService = view.findViewById<LinearLayout>(R.id.btnDetailService)
        val btnDetailPrivacy = view.findViewById<LinearLayout>(R.id.btnDetailPrivacy)

        // 모두 동의 체크 로직
        cbAll.setOnCheckedChangeListener { _, isChecked ->
            cbService.isChecked   = isChecked
            cbPrivacy.isChecked   = isChecked
            cbMarketing.isChecked = isChecked
            updateNextButton(btnNext, cbService, cbPrivacy)
        }

        val syncAll = { _: android.widget.CompoundButton, _: Boolean ->
            cbAll.setOnCheckedChangeListener(null)
            cbAll.isChecked = cbService.isChecked && cbPrivacy.isChecked && cbMarketing.isChecked
            cbAll.setOnCheckedChangeListener { _, checked ->
                cbService.isChecked   = checked
                cbPrivacy.isChecked   = checked
                cbMarketing.isChecked = checked
                updateNextButton(btnNext, cbService, cbPrivacy)
            }
            updateNextButton(btnNext, cbService, cbPrivacy)
        }

        cbService.setOnCheckedChangeListener(syncAll)
        cbPrivacy.setOnCheckedChangeListener(syncAll)
        cbMarketing.setOnCheckedChangeListener(syncAll)

        updateNextButton(btnNext, cbService, cbPrivacy)

        // 약관 상세보기 버튼 클릭 이벤트
        btnDetailService.setOnClickListener {
            showTermsDialog("서비스 이용약관", textServiceTerms)
        }
        btnDetailPrivacy.setOnClickListener {
            showTermsDialog("개인정보 수집 및 이용 동의", textPrivacyTerms)
        }
    }

    private fun updateNextButton(btn: MaterialButton, cbService: CheckBox, cbPrivacy: CheckBox) {
        val enabled = cbService.isChecked && cbPrivacy.isChecked
        btn.isEnabled = enabled
        btn.alpha = if (enabled) 1f else 0.4f
    }

    // 바텀시트 띄우는 함수
    private fun showTermsDialog(title: String, content: String) {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.layout_bottom_sheet_terms, null)

        view.findViewById<TextView>(R.id.tvSheetTitle).text = title
        view.findViewById<TextView>(R.id.tvSheetContent).text = content

        view.findViewById<MaterialButton>(R.id.btnSheetClose).setOnClickListener {
            dialog.dismiss()
        }

        dialog.setContentView(view)
        dialog.show()
    }

    // --- 우리가 정리한 약관 데이터 ---
    private val textServiceTerms = """
        제1장 총칙
        
        제1조 (목적)
        본 약관은 한국외대 크로커다일팀(이하 “팀”)이 제공하는 실시간 보이스피싱 탐지 서비스 ‘CROCODILE’(이하 “서비스”)의 이용과 관련하여 팀과 이용자 간의 권리, 의무 및 책임사항, 기타 필요한 사항을 규정함을 목적으로 합니다.
        
        제2조 (용어의 정의)
        1. “서비스”라 함은 구현되는 단말기와 상관없이 이용자가 이용할 수 있는 CROCODILE 앱 및 관련 제반 서비스를 의미합니다.
        2. “이용자”란 본 약관에 동의하고 앱을 설치하여 “팀”이 제공하는 “서비스”를 이용하는 자를 말합니다.
        3. “위험상황”은 보이스피싱으로 의심되는 통화 내용이나 스미싱 위험이 있는 문자 등 서비스에서 감지할 수 있는 사기 의심 상황을 의미합니다.
        
        제3조 (서비스의 제공 및 변경)
        1. 팀은 이용자에게 실시간 음성 분석 및 통화 기록 기반의 보이스피싱 탐지 서비스를 무료로 제공합니다.
        2. 팀은 더 나은 서비스 제공을 위해 서비스의 내용을 변경하거나 업데이트할 수 있습니다.
        
        제4조 (안전안심 서비스의 한계 및 면책)
        1. 본 서비스는 이용자에게 금융피해가 발생할 것으로 의심되는 정보를 제공하는 보조적인 예방 서비스이며, 모든 보이스피싱 및 스미싱의 완벽한 차단이나 인명, 재산 보호를 절대적으로 보증하지 않습니다.
        2. 팀은 천재지변, 통신 장애, 기타 불가항력적 사유로 서비스를 제공할 수 없는 경우 책임이 면제되며, 이용자의 귀책사유로 인한 불이익이나 본 서비스의 경고를 무시하여 발생한 피해에 대해 법적 책임을 지지 않습니다.
        
        제5조 (이용자의 의무)
        이용자는 본 약관 및 관계 법령을 준수하여야 하며, 서비스를 임의로 조작하거나 리버스 엔지니어링 하는 등 팀의 업무를 방해하는 행위를 해서는 안 됩니다.
    """.trimIndent()

    private val textPrivacyTerms = """
        CROCODILE 서비스 제공을 위해 한국외대 크로커다일팀은 다음과 같이 개인정보를 수집 및 이용합니다. 내용을 자세히 읽으신 후 동의해 주시기 바랍니다.
        
        [수집 및 이용 목적]
        - 실시간 보이스피싱 및 스미싱 위험 탐지
        - 위험 번호 식별 및 경고 알림(PUSH) 제공
        - 서비스 안정성 확보 및 기능 개선
        
        [수집하는 항목]
        - 기기 정보 : 단말기 모델명, OS 버전
        - 통화 및 연락처 : 휴대전화번호, 통화 기록(Call Log), 연락처 정보
        - 음성 데이터 : 마이크를 통한 실시간 통화 음성
          ※ 음성 데이터는 기기 내부(On-Device)에서 실시간 분석에만 사용되며, 어떠한 경우에도 외부 서버로 전송되거나 저장되지 않습니다.
        
        [보유 및 이용 기간]
        - 서비스 이용 기간 (앱 삭제 시 즉시 파기)
    """.trimIndent()
}