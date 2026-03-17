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
                    navController.navigate(NavRoutes.studyLearning(sid, sentenceId))
                }
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
                navArgument("sentenceId") { type = NavType.LongType }
            )
        ) { backStack ->
            val sessionId = backStack.arguments?.getLong("sessionId") ?: return@composable
            val sentenceId = backStack.arguments?.getLong("sentenceId") ?: return@composable
            StudyLearningScreen(
                sessionId = sessionId,
                sentenceId = sentenceId,
                onNavigateToHighlight = {
                    navController.navigate(NavRoutes.studyHighlight(sessionId, sentenceId))
                },
                onSessionEnd = {
                    navController.navigate(NavRoutes.studyReport(sessionId)) {
                        popUpTo(NavRoutes.STUDY_SESSION) { inclusive = false }
                    }
                }
            )
        }

        composable(
            route = NavRoutes.STUDY_HIGHLIGHT,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.LongType },
                navArgument("sentenceId") { type = NavType.LongType }
            )
        ) { backStack ->
            val sessionId = backStack.arguments?.getLong("sessionId") ?: return@composable
            val sentenceId = backStack.arguments?.getLong("sentenceId") ?: return@composable
            StudyHighlightScreen(
                sessionId = sessionId,
                sentenceId = sentenceId,
                onNextMode = {
                    navController.navigate(NavRoutes.studyLearning(sessionId, sentenceId)) {
                        popUpTo(NavRoutes.STUDY_HIGHLIGHT) { inclusive = true }
                    }
                },
                onSessionEnd = {
                    navController.navigate(NavRoutes.studyReport(sessionId)) {
                        popUpTo(NavRoutes.STUDY_SESSION) { inclusive = false }
                    }
                },
                onRetryRecording = {
                    navController.navigate(NavRoutes.studyLearning(sessionId, sentenceId)) {
                        popUpTo(NavRoutes.STUDY_HIGHLIGHT) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = NavRoutes.STUDY_REPORT,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
        ) { backStack ->
            val sessionId = backStack.arguments?.getLong("sessionId") ?: return@composable
            StudyReportScreen(
                sessionId = sessionId,
                onBackToSession = {
                    navController.navigate("home_graph") {
                        popUpTo(NavRoutes.STUDY_GRAPH) { inclusive = true }
                    }
                }
            )
        }
    }
}