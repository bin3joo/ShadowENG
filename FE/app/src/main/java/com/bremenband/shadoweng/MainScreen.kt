package com.bremenband.shadoweng

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.bremenband.shadoweng.navigation.RootNavGraph

sealed class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    object Home   : BottomNavItem("home_graph",   "홈",        Icons.Default.Home)
    object MyPage : BottomNavItem("mypage_graph", "학습", Icons.Default.PlayArrow)
    object Stats  : BottomNavItem("stats_graph",  "학습 통계",  Icons.Default.Add)
    object MyInfo : BottomNavItem("myinfo_graph", "내 정보",    Icons.Default.Person)
}

val bottomNavItems = listOf(
    BottomNavItem.Home,
    BottomNavItem.MyPage,
    BottomNavItem.Stats,
    BottomNavItem.MyInfo
)

// 하단 네비게이션 바를 숨길 route 목록
private val hideBottomBarRoutes = setOf(
    "game_home",
    "game_level_select",
    "game_play/{level}",
    "game_play/{level}/{prevBest}",
    "game_result/{level}/{hearts}/{result}/{prevBest}",
    "game_leaderboard"
)

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = currentDestination?.hierarchy?.none {
        it.route == "auth_graph"
    } == true && currentDestination?.route !in hideBottomBarRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            RootNavGraph(navController = navController)
        }
    }
}