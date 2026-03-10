package com.bremenband.shadoweng.navigation

import android.net.Uri

object NavRoutes {
    // Study
    const val STUDY_SESSION = "study_session"
    const val STUDY_LEARNING = "study_learning/{sentenceId}"
    const val STUDY_LOADING = "study_loading/{sentenceId}"
    const val STUDY_HIGHLIGHT = "study_highlight/{sentenceId}"
    const val STUDY_REPORT = "study_report/{sessionId}"

    // content
    const val CONTENT_REGISTER = "content_register"
    const val CONTENT_RANGE = "content_range/{embedUrl}"
    fun contentRange(embedUrl: String) = "content_range/${Uri.encode(embedUrl)}"

    // helpers
    fun studyLearning(sentenceId: Long) = "study_learning/$sentenceId"
    fun studyLoading(sentenceId: Long) = "study_loading/$sentenceId"
    fun studyHighlight(sentenceId: Long) = "study_highlight/$sentenceId"
    fun studyReport(sessionId: Long) = "study_report/$sessionId"

    //
    const val REVIEW_SESSION = "review_session"
    const val REVIEW_LEARNING = "review_learning/{sentenceId}"
    const val REVIEW_HIGHLIGHT = "review_highlight/{sentenceId}"
    fun reviewLearning(sentenceId: Long) = "review_learning/$sentenceId"
    fun reviewHighlight(sentenceId: Long) = "review_highlight/$sentenceId"


    const val MY_PAGE = "my_page"
    const val HOME = "home"
    const val AUTH = "auth"
}