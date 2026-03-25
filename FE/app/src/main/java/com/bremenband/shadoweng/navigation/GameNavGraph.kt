package com.bremenband.shadoweng.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.bremenband.shadoweng.feature.game.domain.model.GameFinalResult
import com.bremenband.shadoweng.feature.game.presentation.home.GameHomeScreen
import com.bremenband.shadoweng.feature.game.presentation.leaderboard.LeaderboardScreen
import com.bremenband.shadoweng.feature.game.presentation.levelselect.GameLevelSelectScreen
import com.bremenband.shadoweng.feature.game.presentation.play.GamePlayScreen
import com.bremenband.shadoweng.feature.game.presentation.result.GameResultScreen
import com.google.gson.Gson

fun NavGraphBuilder.gameNavGraph(navController: NavHostController) {
    val gson = Gson()

    navigation(
        startDestination = "game_home",
        route = "game_graph"
    ) {
        composable("game_home") {
            GameHomeScreen(
                onNavigateToPlay = {
                    navController.navigate("game_level_select") {
                        popUpTo("game_home") { inclusive = false }
                        launchSingleTop = true  // 이미 있으면 재생성 안 함
                    }
                },
                onNavigateToLeaderboard = { navController.navigate("game_leaderboard") },
                onNavigateBack = {
                    navController.navigate("home_graph") {
                        popUpTo("game_graph") { inclusive = true }
                    }
                }
            )
        }

        composable("game_level_select") { backStackEntry ->
            // LevelSelect에서 prevBest 접근을 위해 uiState는 Screen 내부에서 처리
            GameLevelSelectScreen(
                onNavigateToPlay = { level, prevBest ->
                    navController.navigate("game_play/$level/$prevBest")
                },
                onNavigateBack = { navController.popBackStack() },
                onNavigateToLeaderboard = {
                    navController.navigate("game_leaderboard")
                }
            )
        }

        composable(
            route = "game_play/{level}/{prevBest}",
            arguments = listOf(
                navArgument("level") { type = NavType.IntType },
                navArgument("prevBest") { type = NavType.FloatType; defaultValue = 0f }
            )
        ) { backStackEntry ->
            val level = backStackEntry.arguments?.getInt("level") ?: 1
            GamePlayScreen(
                onNavigateToResult = { finalResult, hearts, prevBest ->  // prevBest 람다로 받음
                    val json = gson.toJson(finalResult)
                    val encoded = java.net.URLEncoder.encode(json, "UTF-8")
                    navController.navigate("game_result/$level/$hearts/$encoded/$prevBest") {
                        popUpTo("game_play/{level}/{prevBest}") { inclusive = true }
                    }
                },
                onNavigateToLeaderboard = {
                    navController.navigate("game_leaderboard") {
                        popUpTo("game_play/{level}/{prevBest}") { inclusive = true }
                    }
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "game_result/{level}/{hearts}/{result}/{prevBest}",
            arguments = listOf(
                navArgument("level") { type = NavType.IntType },
                navArgument("hearts") { type = NavType.IntType },
                navArgument("result") { type = NavType.StringType },
                navArgument("prevBest") { type = NavType.FloatType; defaultValue = 0f }
            )
        ) { backStackEntry ->
            val level = backStackEntry.arguments?.getInt("level") ?: 1
            val hearts = backStackEntry.arguments?.getInt("hearts") ?: 0
            val prevBest = backStackEntry.arguments?.getFloat("prevBest") ?: 0f
            val json = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("result") ?: "", "UTF-8"
            )
            val finalResult = gson.fromJson(json, GameFinalResult::class.java)
            GameResultScreen(
                finalResult = finalResult,
                hearts = hearts,
                level = level,
                previousBestScore = if (prevBest > 0f) prevBest.toDouble() else null,
                onRetry = {
                    navController.navigate("game_play/$level/$prevBest") {
                        popUpTo("game_result/{level}/{hearts}/{result}/{prevBest}") { inclusive = true }
                    }
                },
                onNextLevel = {
                    navController.navigate("game_play/${level + 1}/0") {
                        popUpTo("game_result/{level}/{hearts}/{result}/{prevBest}") { inclusive = true }
                    }
                },
                onFinish = {
                    navController.navigate("game_level_select") {
                        popUpTo("game_level_select") { inclusive = false }  // level_select 위 스택만 제거
                        launchSingleTop = true
                    }
                },
                onNavigateToLevelSelect = {
                    navController.navigate("game_level_select") {
                        popUpTo("game_level_select") { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onNavigateToLeaderboard = {
                    navController.navigate("game_leaderboard") {
                        popUpTo("game_result/{level}/{hearts}/{result}/{prevBest}") { inclusive = true }
                    }
                }
            )
        }

        composable("game_leaderboard") {
            LeaderboardScreen(
                onNavigateToPlay = {
                    navController.navigate("game_level_select") {
                        popUpTo("game_leaderboard") { inclusive = true }
                    }
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}