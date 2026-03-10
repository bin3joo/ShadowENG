package com.bremenband.shadoweng.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.bremenband.shadoweng.feature.mypage.screen.MyPageScreen

fun NavGraphBuilder.myPageNavGraph(navController: NavHostController) {
    navigation(
        startDestination = NavRoutes.MY_PAGE,
        route = "mypage_graph"
    ) {
        composable(NavRoutes.MY_PAGE) {
            MyPageScreen(
                onNavigateToContent = { sessionId ->
                    navController.navigate("study_graph/$sessionId")
                }
            )
        }
    }
}