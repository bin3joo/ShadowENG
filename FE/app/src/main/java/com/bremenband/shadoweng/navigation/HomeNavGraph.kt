package com.bremenband.shadoweng.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.bremenband.shadoweng.feature.home.presentation.HomeScreen

fun NavGraphBuilder.homeNavGraph(navController: NavHostController) {
    navigation(
        startDestination = NavRoutes.HOME,
        route = "home_graph"
    ) {
        composable(NavRoutes.HOME) {
            HomeScreen(
                onNavigateToStudy = { sessionId ->
                    navController.navigate("study_graph/$sessionId")
                },
                onNavigateToGame = {
                    // TODO: game_graph 연결
                },
                onNavigateToRegister = { navController.navigate("content_graph") },
                onNavigateToMoreSessions = { navController.navigate("mypage_graph") }
            )
        }
    }
}