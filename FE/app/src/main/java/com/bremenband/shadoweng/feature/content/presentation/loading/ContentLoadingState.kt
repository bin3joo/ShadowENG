package com.bremenband.shadoweng.feature.content.presentation.loading

enum class ContentAnalysisStep(val label: String) {
    STT("음성을 텍스트로 변환하고 있어요"),
    SENTENCE_SPLIT("따라 말할 문장을 준비하고 있어요"),
    READY("곧 이 장면으로 쉐도잉을 시작할 수 있어요")
}

enum class ContentStepStatus { PENDING, IN_PROGRESS, DONE }

data class ContentAnalysisStepState(
    val step: ContentAnalysisStep,
    val status: ContentStepStatus = ContentStepStatus.PENDING
)

data class ContentLoadingUiState(
    val steps: List<ContentAnalysisStepState> = ContentAnalysisStep.entries.map { ContentAnalysisStepState(it) },
    val progress: Float = 0f,
    val isDone: Boolean = false
)