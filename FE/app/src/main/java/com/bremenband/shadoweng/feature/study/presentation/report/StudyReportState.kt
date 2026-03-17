package com.bremenband.shadoweng.feature.study.presentation.report

import com.bremenband.shadoweng.feature.study.domain.StudyReport

data class StudyReportUiState(
    val report: StudyReport? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)