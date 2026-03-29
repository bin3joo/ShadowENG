package com.bremenband.shadoweng.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.bremenband.shadoweng.feature.stats.StatsScreen
import com.bremenband.shadoweng.feature.study.presentation.report.StudyReportScreen

fun NavGraphBuilder.statsNavGraph(navController: NavHostController) {
    navigation(
        startDestination = NavRoutes.STATS,
        route = "stats_graph"
    ) {
        composable(NavRoutes.STATS) {
            StatsScreen(
                onNavigateToReport = { sessionId, reportId ->
                    navController.navigate(NavRoutes.studyReport(sessionId, reportId))
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
                onBackToSession = { navController.popBackStack() }
            )
        }
    }
}