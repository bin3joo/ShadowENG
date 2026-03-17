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
            StudyLearningScreen(
                sessionId = 0L, // TODO: sessionId route에 추가 후 연동
                sentenceId = sentenceId,
                onNavigateToHighlight = { }, // TODO: 하이라이팅 구현 후 활성화
                onSessionEnd = {
                    // TODO: 실제 sessionId로 교체
                    navController.navigate(NavRoutes.studyReport(sentenceId)) {
                        popUpTo(NavRoutes.STUDY_SESSION) { inclusive = false }
                    }
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
                        popUpTo(NavRoutes.STUDY_SESSION) { inclusive = false }
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
                        popUpTo(NavRoutes.STUDY_GRAPH) { inclusive = true }
                    }
                }
            )
        }
    }
}