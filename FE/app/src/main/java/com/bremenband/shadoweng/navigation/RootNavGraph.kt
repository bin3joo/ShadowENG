package com.bremenband.shadoweng.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost

@Composable
fun RootNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = "auth_graph"
    ) {
        authNavGraph(navController)
        contentNavGraph(navController)
        studyNavGraph(navController)
        homeNavGraph(navController)
        //reviewNavGraph(navController)
        myPageNavGraph(navController)
    }
}