package com.bremenband.shadoweng.feature.study.presentation.loading

enum class AnalysisStep(val label: String) {
    STT("음성을 텍스트로 변환하고 있어요"),
    SENTENCE_SPLIT("따라 말할 문장을 준비하고 있어요"),
    READY("곧 이 장면으로 쉐도잉을 시작할 수 있어요")
}

enum class StepStatus { PENDING, IN_PROGRESS, DONE }

data class AnalysisStepState(
    val step: AnalysisStep,
    val status: StepStatus = StepStatus.PENDING
)

data class StudyLoadingUiState(
    val steps: List<AnalysisStepState> = AnalysisStep.entries.map { AnalysisStepState(it) },
    val progress: Float = 0f,
    val isDone: Boolean = false
)