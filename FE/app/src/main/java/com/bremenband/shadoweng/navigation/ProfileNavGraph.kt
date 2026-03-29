package com.bremenband.shadoweng.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.bremenband.shadoweng.feature.profile.presentation.MyInfoScreen

fun NavGraphBuilder.profileNavGraph(navController: NavHostController) {
    navigation(
        startDestination = NavRoutes.MY_INFO,
        route = "profile_graph"
    ) {
        composable(NavRoutes.MY_INFO) {
            MyInfoScreen(
                onNavigateToStats = {
                    navController.navigate("stats_graph")
                },
                onNavigateToLeaderboard = {
                    navController.navigate("game_leaderboard")
                },
                onNavigateToLogin = {
                    navController.navigate("auth_graph") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}