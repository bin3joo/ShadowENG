package com.bremenband.shadoweng

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.bremenband.shadoweng.navigation.NavRoutes
import com.bremenband.shadoweng.navigation.RootNavGraph
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed class BottomNavItem(
    val route: String,
    val label: String,
    @DrawableRes val icon: Int
) {
    // 변경 후
    object Home   : BottomNavItem("home_graph",    "홈",        R.drawable.home)
    object MyPage : BottomNavItem("mypage_graph",  "마이 쉐도잉", R.drawable.my_shadoweng)
    object Stats  : BottomNavItem("stats_graph",   "학습 통계",  R.drawable.stats)
    object MyInfo : BottomNavItem("profile_graph", "내 정보",    R.drawable.profile)
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
    "game_leaderboard",
    NavRoutes.SPLASH,
    NavRoutes.SET_NICKNAME
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
                NavigationBar(
                    containerColor = Color.White,
                    tonalElevation = 0.dp
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    painter = painterResource(id = item.icon),
                                    contentDescription = item.label,
                                    modifier = Modifier.size(24.dp),
                                    tint = if (selected) Color(0xFFFF5D5D) else Color(0xFFAAAAAA)
                                )
                            },
                            label = {
                                Text(
                                    item.label,
                                    fontSize = 10.sp,
                                    color = if (selected) Color(0xFFFF5D5D) else Color(0xFFAAAAAA)
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Color(0xFFFF5D5D).copy(alpha = 0.1f),
                                selectedIconColor = Color(0xFFFF5D5D),
                                unselectedIconColor = Color(0xFFAAAAAA)
                            )
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