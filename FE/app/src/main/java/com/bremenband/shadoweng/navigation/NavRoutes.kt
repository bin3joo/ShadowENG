package com.bremenband.shadoweng.navigation

import android.net.Uri

object NavRoutes {
    // Study
    const val STUDY_LEARNING = "study_learning/{sessionId}/{sentenceId}/{step}"
    const val STUDY_LOADING = "study_loading/{sessionId}"
    const val STUDY_HIGHLIGHT = "study_highlight/{sessionId}/{sentenceId}/{step}"
    const val STUDY_REPORT = "study_report/{sessionId}/{reportId}"

    const val STUDY_GRAPH = "study_graph"
    const val STUDY_SESSION = "study_session/{sessionId}"
    fun studySession(sessionId: Long) = "study_session/$sessionId"
    fun studyLoading(sessionId: Long) = "study_loading/$sessionId"
    fun studyReport(sessionId: Long, reportId: Long) = "study_report/$sessionId/$reportId"


    // content
    const val CONTENT_REGISTER = "content_register"
    const val CONTENT_RANGE = "content_range/{embedUrl}"
    const val CONTENT_LOADING = "content_loading/{embedUrl}/{startSec}/{endSec}"

    fun contentRange(embedUrl: String) = "content_range/${Uri.encode(embedUrl)}"
    fun contentLoading(embedUrl: String, startSec: Double, endSec: Double) =
        "content_loading/${Uri.encode(embedUrl)}/$startSec/$endSec"

    // helpers
    fun studyLearning(sessionId: Long, sentenceId: Long, step: Int = 1) =
        "study_learning/$sessionId/$sentenceId/$step"
    fun studyHighlight(sessionId: Long, sentenceId: Long, step: Int) =
        "study_highlight/$sessionId/$sentenceId/$step"
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
    const val STATS = "stats"
    const val MY_INFO = "my_info"
    const val STREAK_CALENDAR = "streak_calendar"

    const val STATS_REPORT = "stats_report/{sessionId}"
    fun statsReport(sessionId: Long) = "stats_report/$sessionId"
}