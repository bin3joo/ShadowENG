package com.bremenband.shadoweng.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.bremenband.shadoweng.feature.mypage.presentation.bookmark.BookmarkScreen
import com.bremenband.shadoweng.feature.mypage.presentation.mypage.MyPageScreen
import com.bremenband.shadoweng.feature.mypage.presentation.sessions.SessionListScreen

fun NavGraphBuilder.myPageNavGraph(navController: NavHostController) {
    navigation(
        startDestination = NavRoutes.MY_PAGE,
        route = "mypage_graph"
    ) {
        composable(NavRoutes.MY_PAGE) {
            MyPageScreen(
                onNavigateToStudy = { sessionId ->
                    navController.navigate(NavRoutes.studySession(sessionId))
                },
                onNavigateToRegister = { navController.navigate("content_graph") },
                onNavigateToGame = { navController.navigate("game_graph") },
                onNavigateToBookmark = { navController.navigate("bookmark") },
                onNavigateToActiveSessions = { navController.navigate("session_list/false") },
                onNavigateToCompletedSessions = { navController.navigate("session_list/true") }
            )
        }

        composable("bookmark") {
            BookmarkScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(
            route = "session_list/{isReview}",
            arguments = listOf(navArgument("isReview") { type = NavType.BoolType })
        ) { backStackEntry ->
            val isReview = backStackEntry.arguments?.getBoolean("isReview") ?: false
            SessionListScreen(
                isReview = isReview,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToStudy = { sessionId ->
                    navController.navigate(NavRoutes.studySession(sessionId))
                }
            )
        }
    }
}