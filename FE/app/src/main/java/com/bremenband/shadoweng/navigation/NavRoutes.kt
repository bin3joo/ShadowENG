package com.bremenband.shadoweng.navigation

import android.net.Uri

object NavRoutes {
    // Study
    const val STUDY_LEARNING = "study_learning/{sentenceId}" // TODO: sessionId 추가 필요
    const val STUDY_LOADING = "study_loading/{sessionId}"
    const val STUDY_HIGHLIGHT = "study_highlight/{sentenceId}"
    const val STUDY_REPORT = "study_report/{sessionId}"

    const val STUDY_GRAPH = "study_graph"
    const val STUDY_SESSION = "study_session/{sessionId}"
    fun studySession(sessionId: Long) = "study_session/$sessionId"
    fun studyLoading(sessionId: Long) = "study_loading/$sessionId"

    // content
    const val CONTENT_REGISTER = "content_register"
    const val CONTENT_RANGE = "content_range/{embedUrl}"
    const val CONTENT_LOADING = "content_loading/{sessionId}"

    fun contentRange(embedUrl: String) = "content_range/${Uri.encode(embedUrl)}"
    fun contentLoading(sessionId: Long) = "content_loading/$sessionId"
    // helpers
    fun studyLearning(sentenceId: Long) = "study_learning/$sentenceId"
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