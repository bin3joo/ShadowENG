package com.bremenband.shadoweng.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.bremenband.shadoweng.feature.content.screen.ContentRangeScreen
import com.bremenband.shadoweng.feature.content.screen.ContentRegisterScreen

fun NavGraphBuilder.contentNavGraph(navController: NavHostController) {
    navigation(
        startDestination = NavRoutes.CONTENT_REGISTER,
        route = "content_graph"
    ) {
        composable(NavRoutes.CONTENT_REGISTER) {
            ContentRegisterScreen(
                onNavigateToRange = { videoId ->
                    navController.navigate(NavRoutes.contentRange(videoId))
                }
            )
        }

        composable(
            route = NavRoutes.CONTENT_RANGE,
            arguments = listOf(navArgument("embedUrl") { type = NavType.StringType })
        ) { backStack ->
            val embedUrl = backStack.arguments?.getString("embedUrl") ?: return@composable
            ContentRangeScreen(
                embedUrl = embedUrl,
                onNavigateToStudy = { sessionId ->
                    navController.navigate("study_graph/$sessionId") {
                        popUpTo("content_graph") { inclusive = true }
                    }
                }
            )
        }
    }
}