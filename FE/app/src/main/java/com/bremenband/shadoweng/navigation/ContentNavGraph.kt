package com.bremenband.shadoweng.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.bremenband.shadoweng.feature.content.presentation.loading.ContentLoadingScreen
import com.bremenband.shadoweng.feature.content.presentation.range.ContentRangeScreen
import com.bremenband.shadoweng.feature.content.presentation.register.ContentRegisterScreen

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
                onNavigateToStudy = { sid ->
                    navController.navigate(NavRoutes.contentLoading(sid)) {  // studySession → contentLoading
                        popUpTo(NavRoutes.CONTENT_RANGE) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = NavRoutes.CONTENT_LOADING,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
        ) { backStack ->
            val sessionId = backStack.arguments?.getLong("sessionId") ?: return@composable
            ContentLoadingScreen(
                sessionId = sessionId,
                onNavigateToStudy = { sid ->
                    navController.navigate(NavRoutes.studySession(sid)) {
                        popUpTo("content_graph") { inclusive = true }
                    }
                }
            )
        }
    }
}