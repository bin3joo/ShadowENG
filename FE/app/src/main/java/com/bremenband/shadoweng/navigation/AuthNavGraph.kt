package com.bremenband.shadoweng.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.bremenband.shadoweng.feature.auth.presentation.AuthScreen
import com.bremenband.shadoweng.feature.auth.presentation.SplashScreen
import com.bremenband.shadoweng.feature.auth.presentation.SetNicknameScreen
fun NavGraphBuilder.authNavGraph(navController: NavHostController) {
    navigation(
        startDestination = NavRoutes.SPLASH,
        route = "auth_graph"
    ) {
        composable(NavRoutes.SPLASH) {
            SplashScreen(
                onNavigateToHome = {
                    navController.navigate("home_graph") {
                        popUpTo("auth_graph") { inclusive = true }
                    }
                },
                onNavigateToAuth = {
                    navController.navigate(NavRoutes.AUTH) {
                        popUpTo(NavRoutes.SPLASH) { inclusive = true }
                    }
                }
            )
        }
        composable(NavRoutes.AUTH) {
            AuthScreen(
                onLoginSuccess = {
                    navController.navigate("home_graph") {
                        popUpTo("auth_graph") { inclusive = true }
                    }
                },
                onNewUser = {
                    navController.navigate(NavRoutes.SET_NICKNAME)
                }
            )
        }
        composable(NavRoutes.SET_NICKNAME) {
            SetNicknameScreen(
                onComplete = {
                    navController.navigate("home_graph") {
                        popUpTo("auth_graph") { inclusive = true }
                    }
                }
            )
        }
    }
}