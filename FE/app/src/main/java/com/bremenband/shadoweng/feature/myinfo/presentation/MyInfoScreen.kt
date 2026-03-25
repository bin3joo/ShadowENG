package com.bremenband.shadoweng.feature.myinfo.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bremenband.shadoweng.R
import com.bremenband.shadoweng.core.ui.util.tierToDrawable

@Composable
fun MyInfoScreen(
    onNavigateToCalendar: () -> Unit = {},
    onNavigateToLeaderboard: () -> Unit = {},
    onNavigateToLogin: () -> Unit = {},
    viewModel: MyInfoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val appVersion = "1.0.0"

    LaunchedEffect(Unit) {
        viewModel.navigateToLogin.collect { onNavigateToLogin() }
    }

    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("로그아웃", fontWeight = FontWeight.Bold, color = Color(0xFF362000)) },
            text = { Text("정말 로그아웃 하시겠어요?", color = Color(0xFF666666)) },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    viewModel.onEvent(MyInfoEvent.ClickLogout)
                }) {
                    Text("로그아웃", color = Color(0xFFFF5D5D), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("취소", color = Color(0xFF888888))
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFFFEDF57))
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F3))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF5F5F3))
                .padding(horizontal = 20.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("내 정보", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF362000))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 프로필 카드
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFFF8E1)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(tierToDrawable(uiState.tier)),
                            contentDescription = null,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            uiState.nickname,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF362000)
                        )
                        Text(uiState.email, fontSize = 13.sp, color = Color(0xFF888888))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFFFEDF57).copy(alpha = 0.2f))
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            Text(
                                tierDisplayName(uiState.tier),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF362000)
                            )
                        }
                    }
                }
            }

            // 학습 현황 카드
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onNavigateToCalendar() },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("학습 현황", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF362000))
                        Icon(
                            imageVector = Icons.Default.ArrowForwardIos,
                            contentDescription = null,
                            tint = Color(0xFFBBBBBB),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        SummaryItem(
                            icon = { Image(painterResource(R.drawable.engmu), null, modifier = Modifier.size(26.dp)) },
                            value = "${uiState.totalVisitedDays}",
                            label = "총 출석일"
                        )
                        SummaryDivider()
                        SummaryItem(
                            icon = { Text("🔥", fontSize = 22.sp) },
                            value = "${uiState.longestStreak}",
                            label = "최장 스트릭"
                        )
                        SummaryDivider()
                        SummaryItem(
                            icon = {
                                Icon(
                                    Icons.Default.Bookmark,
                                    contentDescription = null,
                                    tint = Color(0xFFFF5D5D),
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            value = "${uiState.bookmarkCount}",
                            label = "북마크"
                        )
                    }
                }
            }

            // 게임 현황 카드
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onNavigateToLeaderboard() },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFF8E1)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(tierToDrawable(uiState.tier)),
                                contentDescription = null,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("게임 현황", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF362000))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(Color(0xFFFEDF57).copy(alpha = 0.2f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        tierDisplayName(uiState.tier),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF362000)
                                    )
                                }
                                Text("주간 ${uiState.weeklyScore}점", fontSize = 12.sp, color = Color(0xFF888888))
                            }
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ArrowForwardIos,
                        contentDescription = null,
                        tint = Color(0xFFBBBBBB),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                "설정",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF888888),
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column {
                    SettingRow(
                        icon = Icons.Default.Notifications,
                        iconTint = Color(0xFFFF5D5D),
                        label = "알림 설정",
                        onClick = { /* TODO */ }
                    )
                    HorizontalDivider(color = Color(0xFFF0F0F0), modifier = Modifier.padding(horizontal = 16.dp))
                    SettingRow(
                        icon = Icons.Default.Smartphone,
                        iconTint = Color(0xFF888888),
                        label = "앱 버전",
                        trailing = { Text(appVersion, fontSize = 13.sp, color = Color(0xFF888888)) },
                        showArrow = false,
                        onClick = {}
                    )
                    HorizontalDivider(color = Color(0xFFF0F0F0), modifier = Modifier.padding(horizontal = 16.dp))
                    SettingRow(
                        icon = null,
                        iconTint = Color.Transparent,
                        label = "로그아웃",
                        labelColor = Color(0xFFFF5D5D),
                        showArrow = false,
                        onClick = { showLogoutDialog = true },
                        leadingContent = {
                            Image(
                                painter = painterResource(R.drawable.engmu),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SummaryItem(
    icon: @Composable () -> Unit,
    value: String,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            icon()
        }
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFF5D5D))
        Text(label, fontSize = 11.sp, color = Color(0xFF888888))
    }
}

@Composable
private fun SummaryDivider() {
    Box(modifier = Modifier.width(1.dp).height(48.dp).background(Color(0xFFEEEEEE)))
}

@Composable
private fun SettingRow(
    icon: ImageVector?,
    iconTint: Color,
    label: String,
    labelColor: Color = Color(0xFF362000),
    showArrow: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (leadingContent != null) {
            leadingContent()
        } else if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        }
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = labelColor, modifier = Modifier.weight(1f))
        trailing?.invoke()
        if (showArrow) {
            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFFCCCCCC), modifier = Modifier.size(18.dp))
        }
    }
}

private fun tierDisplayName(tier: String) = when (tier) {
    "BRONZE"     -> "브론즈"
    "SILVER"     -> "실버"
    "GOLD"       -> "골드"
    "PLATINUM"   -> "플래티넘"
    "DIAMOND"    -> "다이아몬드"
    "RUBY"       -> "루비"
    "CHALLENGER" -> "챌린저"
    else         -> "브론즈"
}