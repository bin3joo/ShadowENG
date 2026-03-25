package com.bremenband.shadoweng.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.bremenband.shadoweng.feature.myinfo.presentation.MyInfoScreen

fun NavGraphBuilder.myInfoNavGraph(navController: NavHostController) {
    navigation(
        startDestination = NavRoutes.MY_INFO,
        route = "myinfo_graph"
    ) {
        composable(NavRoutes.MY_INFO) {
            MyInfoScreen(
                onNavigateToCalendar = {
                    navController.navigate(NavRoutes.STREAK_CALENDAR)
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