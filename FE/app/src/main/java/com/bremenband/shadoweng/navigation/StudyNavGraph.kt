package com.bremenband.shadoweng.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.bremenband.shadoweng.feature.study.screen.StudyHighlightScreen
import com.bremenband.shadoweng.feature.study.screen.StudyLearningScreen
import com.bremenband.shadoweng.feature.study.screen.StudyLoadingScreen
import com.bremenband.shadoweng.feature.study.screen.StudyReportScreen
import com.bremenband.shadoweng.feature.study.screen.StudySessionScreen

fun NavGraphBuilder.studyNavGraph(navController: NavHostController) {
    navigation(
        startDestination = "study_session/{sessionId}",
        route = "study_graph/{sessionId}"
    ) {
        composable(
            route = "study_session/{sessionId}",
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
        ) { backStack ->
            val sessionId = backStack.arguments?.getLong("sessionId") ?: return@composable
            StudySessionScreen(
                sessionId = sessionId,
                onStartStudy = { sentenceId ->
                    navController.navigate(NavRoutes.studyLearning(sentenceId))
                }
            )
        }

        composable(
            route = NavRoutes.STUDY_LEARNING,
            arguments = listOf(navArgument("sentenceId") { type = NavType.LongType })
        ) { backStack ->
            val sentenceId = backStack.arguments?.getLong("sentenceId") ?: return@composable
            val sessionId = backStack.arguments?.getLong("sessionId") ?: 0L
            StudyLearningScreen(
                sessionId = sessionId,
                sentenceId = sentenceId,
                onNavigateToHighlight = { navController.navigate(NavRoutes.studyLoading(it)) },
                onSessionEnd = {
                    navController.navigate("home_graph") {
                        popUpTo("study_graph/{sessionId}") { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = NavRoutes.STUDY_LOADING,
            arguments = listOf(navArgument("sentenceId") { type = NavType.LongType })
        ) { backStack ->
            val sentenceId = backStack.arguments?.getLong("sentenceId") ?: return@composable
            StudyLoadingScreen(
                onNavigateToHighlight = {
                    navController.navigate(NavRoutes.studyHighlight(sentenceId)) {
                        popUpTo(NavRoutes.STUDY_LOADING) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = NavRoutes.STUDY_HIGHLIGHT,
            arguments = listOf(navArgument("sentenceId") { type = NavType.LongType })
        ) { backStack ->
            val sentenceId = backStack.arguments?.getLong("sentenceId") ?: return@composable
            StudyHighlightScreen(
                sentenceId = sentenceId,
                onNextMode = {
                    navController.navigate(NavRoutes.studyLearning(sentenceId)) {
                        popUpTo(NavRoutes.STUDY_HIGHLIGHT) { inclusive = true }
                    }
                },
                onSessionEnd = {
                    navController.navigate(NavRoutes.studyReport(sentenceId)) {
                        popUpTo("study_session/{sessionId}") { inclusive = false }
                    }
                },
                onRetryRecording = {
                    navController.navigate(NavRoutes.studyLearning(sentenceId)) {
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
                        popUpTo("study_graph/{sessionId}") { inclusive = true }
                    }
                }
            )
        }
    }
}