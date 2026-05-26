package com.smishing.crocodiledetector

data class TutorialSlide(
    val icon: String,
    val iconBgColor: String,
    val iconBorderColor: String,
    val tag: String,
    val tagColor: String,
    val tagBgColor: String,
    val title: String,
    val description: String
)

object TutorialData {
    fun getSlides() = listOf(
        TutorialSlide(
            icon = "📞",
            iconBgColor = "#1A1030",
            iconBorderColor = "#A78BFA",
            tag = "STEP 1",
            tagColor = "#A78BFA",
            tagBgColor = "#1A1030",
            title = "모르는 번호 감지",
            description = "연락처에 없는 번호로 전화가 오면\n백그라운드에서 자동으로\nSTT 엔진이 작동합니다"
        ),
        TutorialSlide(
            icon = "🔍",
            iconBgColor = "#0E2020",
            iconBorderColor = "#00C896",
            tag = "STEP 2",
            tagColor = "#00C896",
            tagBgColor = "#0A2020",
            title = "위험 키워드 실시간 탐지",
            description = "상대방 발화를 실시간으로 분석해\n보이스피싱 관련 키워드가 감지되면\n오버레이 화면이 활성화됩니다"
        ),
        TutorialSlide(
            icon = "🤖",
            iconBgColor = "#1A1020",
            iconBorderColor = "#FF6B9D",
            tag = "STEP 3",
            tagColor = "#FF6B9D",
            tagBgColor = "#1A0A18",
            title = "AI 대응 스크립트 생성",
            description = "상대방의 발화 내용이 서버로 전송되어\nAI가 상황에 맞는 대응 스크립트를\n실시간으로 제공합니다"
        ),
        TutorialSlide(
            icon = "💬",
            iconBgColor = "#101A10",
            iconBorderColor = "#4ADE80",
            tag = "목표",
            tagColor = "#4ADE80",
            tagBgColor = "#0A180A",
            title = "대화 주도권 되찾기",
            description = "AI 스크립트를 활용해 범죄자가\n유도하는 대화 흐름을 끊고\n피해를 사전에 차단합니다"
        )
    )
}