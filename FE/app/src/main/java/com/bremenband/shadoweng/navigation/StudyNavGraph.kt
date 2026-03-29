package com.bremenband.shadoweng.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.bremenband.shadoweng.feature.study.presentation.highlight.StudyHighlightScreen
import com.bremenband.shadoweng.feature.study.presentation.learning.StudyLearningScreen
import com.bremenband.shadoweng.feature.study.presentation.loading.StudyLoadingScreen
import com.bremenband.shadoweng.feature.study.presentation.report.StudyReportScreen
import com.bremenband.shadoweng.feature.study.presentation.session.StudySessionScreen

fun NavGraphBuilder.studyNavGraph(navController: NavHostController) {
    navigation(
        startDestination = NavRoutes.STUDY_SESSION,
        route = NavRoutes.STUDY_GRAPH
    ) {
        composable(
            route = NavRoutes.STUDY_SESSION,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
        ) { backStack ->
            val sessionId = backStack.arguments?.getLong("sessionId") ?: return@composable
            StudySessionScreen(
                sessionId = sessionId,
                onStartStudy = { sid, sentenceId ->
                    navController.navigate(NavRoutes.studyLearning(sid, sentenceId, step = 1)) {
                        popUpTo(NavRoutes.STUDY_SESSION) { inclusive = false }  // 세션은 남기고 learning만 쌓음
                    }
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = NavRoutes.STUDY_LOADING,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
        ) { backStack ->
            val sessionId = backStack.arguments?.getLong("sessionId") ?: return@composable
            StudyLoadingScreen(
                onNavigateToSession = {
                    navController.navigate(NavRoutes.studySession(sessionId)) {
                        popUpTo(NavRoutes.STUDY_LOADING) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = NavRoutes.STUDY_LEARNING,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.LongType },
                navArgument("sentenceId") { type = NavType.LongType },
                navArgument("step") { type = NavType.IntType }
            )
        ) { backStack ->
            val sessionId = backStack.arguments?.getLong("sessionId") ?: return@composable
            val sentenceId = backStack.arguments?.getLong("sentenceId") ?: return@composable
            val step = backStack.arguments?.getInt("step") ?: 1
            StudyLearningScreen(
                sessionId = sessionId,
                sentenceId = sentenceId,
                step = step,
                onNavigateToHighlight = { completedStep ->
                    if (completedStep == 1) {
                        navController.navigate(NavRoutes.studyLearning(sessionId, sentenceId, 2)) {
                            popUpTo(NavRoutes.STUDY_LEARNING) { inclusive = true }
                        }
                    } else {
                        navController.navigate(NavRoutes.studyHighlight(sessionId, sentenceId, completedStep)) {
                            popUpTo(NavRoutes.STUDY_LEARNING) { inclusive = true }
                        }
                    }
                },
                onSessionEnd = {
                    navController.navigate("home_graph") {
                        popUpTo(NavRoutes.STUDY_GRAPH) { inclusive = true }
                    }
                },
                onExit = {
                    navController.navigate("home_graph") {
                        popUpTo(NavRoutes.STUDY_GRAPH) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = NavRoutes.STUDY_HIGHLIGHT,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.LongType },
                navArgument("sentenceId") { type = NavType.LongType },
                navArgument("step") { type = NavType.IntType }
            )
        ) { backStack ->
            val sessionId = backStack.arguments?.getLong("sessionId") ?: return@composable
            val sentenceId = backStack.arguments?.getLong("sentenceId") ?: return@composable
            val step = backStack.arguments?.getInt("step") ?: 1
            StudyHighlightScreen(
                sessionId = sessionId,
                sentenceId = sentenceId,
                step = step,
                onNextMode = {
                    val nextStep = step + 1
                    navController.navigate(NavRoutes.studyLearning(sessionId, sentenceId, nextStep)) {
                        popUpTo(NavRoutes.STUDY_HIGHLIGHT) { inclusive = true }
                    }
                },
                onSessionEnd = { reportId ->
                    if (reportId > 0) {
                        navController.navigate(NavRoutes.studyReport(sessionId, reportId)) {
                            popUpTo(NavRoutes.STUDY_SESSION) { inclusive = false }
                        }
                    } else {
                        navController.navigate("home_graph") {
                            popUpTo(NavRoutes.STUDY_GRAPH) { inclusive = true }
                        }
                    }
                },
                onNextSentence = { nextSentenceId ->
                    if (nextSentenceId == -1L) {
                        navController.popBackStack(NavRoutes.STUDY_SESSION, inclusive = false)
                    } else {
                        navController.navigate(NavRoutes.studyLearning(sessionId, nextSentenceId, 1)) {
                            popUpTo(NavRoutes.STUDY_SESSION) { inclusive = false }
                        }
                    }
                },
                onRetryRecording = {
                    navController.navigate(NavRoutes.studyLearning(sessionId, sentenceId, step)) {
                        popUpTo(NavRoutes.STUDY_HIGHLIGHT) { inclusive = true }
                    }
                },
                onExit = {
                    navController.navigate("home_graph") {
                        popUpTo(NavRoutes.STUDY_GRAPH) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = NavRoutes.STUDY_REPORT,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.LongType },
                navArgument("reportId") { type = NavType.LongType }
            )
        ) { backStack ->
            val sessionId = backStack.arguments?.getLong("sessionId") ?: return@composable
            val reportId = backStack.arguments?.getLong("reportId") ?: return@composable
            StudyReportScreen(
                sessionId = sessionId,
                reportId = reportId,
                onBackToSession = {
                    navController.navigate("home_graph") {
                        popUpTo(NavRoutes.STUDY_GRAPH) { inclusive = true }
                    }
                }
            )
        }
    }
}