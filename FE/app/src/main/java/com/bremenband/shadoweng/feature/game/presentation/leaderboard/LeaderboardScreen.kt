package com.bremenband.shadoweng.feature.game.presentation.leaderboard

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bremenband.shadoweng.R
import com.bremenband.shadoweng.core.ui.util.tierToDrawable
import com.bremenband.shadoweng.feature.game.domain.model.Ranker
import kotlinx.coroutines.launch

enum class ZoneStatus { PROMOTION, SAFE, DEMOTION }

private fun getZoneStatus(rank: Int, totalRankers: Int): ZoneStatus {
    return when {
        totalRankers < 10 -> ZoneStatus.SAFE
        totalRankers < 20 -> when {
            rank <= 10 -> ZoneStatus.PROMOTION
            else -> ZoneStatus.SAFE
        }
        else -> when {
            rank <= 10 -> ZoneStatus.PROMOTION
            rank <= 20 -> ZoneStatus.SAFE
            else -> ZoneStatus.DEMOTION
        }
    }
}

@Composable
fun LeaderboardScreen(
    onNavigateToPlay: () -> Unit,
    onNavigateBack: () -> Unit = {},
    viewModel: LeaderboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.navigateToPlay.collect { onNavigateToPlay() }
    }

    LaunchedEffect(Unit) {
        viewModel.reload()
    }

    val isParticipating = uiState.tier.isNotEmpty() && !uiState.frozen

    val totalRankers = maxOf(
        uiState.topRankers.lastOrNull()?.rank ?: 0,
        uiState.nearbyRankers.lastOrNull()?.rank ?: 0
    )

    // 헤더 입장 애니메이션
    val headerOffsetY = remember { Animatable(80f) }
    val headerAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.coroutineScope {
            launch {
                headerOffsetY.animateTo(0f, animationSpec = tween(600, easing = FastOutSlowInEasing))
            }
            launch {
                headerAlpha.animateTo(1f, animationSpec = tween(600))
            }
        }
    }

    // 티어명 fade in
    val tierNameAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(400)
        tierNameAlpha.animateTo(1f, animationSpec = tween(500))
    }

    // 내 순위로 스크롤
    val listState = rememberLazyListState()
    val myNearbyIndex = uiState.nearbyRankers.indexOfFirst { it.isMe }

    LaunchedEffect(uiState.nearbyRankers) {
        if (myNearbyIndex >= 0 && uiState.nearbyRankers.isNotEmpty()) {
            val myRank = uiState.nearbyRankers.find { it.isMe }?.rank ?: Int.MAX_VALUE
            val isMyRankInTop = myRank <= 5  // 여기서 직접 계산

            val afterTop = 4 + uiState.topRankers.size
            val targetIndex = if (isMyRankInTop) {
                afterTop + myNearbyIndex
            } else {
                afterTop + 1 + myNearbyIndex
            }
            listState.animateScrollToItem(
                index = targetIndex.coerceAtLeast(0),
                scrollOffset = -200
            )
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFf1f8ff))
    ) {
        // 헤더
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .graphicsLayer {
                        translationY = headerOffsetY.value
                        alpha = headerAlpha.value
                    }
            ) {
                Image(
                    painter = painterResource(id = R.drawable.game_leaderboard),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, start = 8.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로", tint = Color(0xFF362000))
                    }
                    Text(
                        "리더 보드",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF362000),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.width(48.dp))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .padding(top = 32.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    // 이전 티어
                    Image(
                        painter = painterResource(tierToDrawable(prevTier(uiState.tier))),
                        contentDescription = null,
                        modifier = Modifier
                            .size(180.dp)
                            .align(Alignment.BottomCenter)
                            .offset(x = (-160).dp)
                            .graphicsLayer { alpha = 0.6f },
                        contentScale = ContentScale.Fit
                    )

                    // 다음 티어
                    Image(
                        painter = painterResource(tierToDrawable(nextTier(uiState.tier))),
                        contentDescription = null,
                        modifier = Modifier
                            .size(180.dp)
                            .align(Alignment.BottomCenter)
                            .offset(x = 160.dp)
                            .graphicsLayer { alpha = 0.6f },
                        contentScale = ContentScale.Fit
                    )

                    // 현재 티어
                    Column(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(-50.dp)
                    ) {
                        Image(
                            painter = painterResource(tierToDrawable(uiState.tier)),
                            contentDescription = uiState.tier,
                            modifier = Modifier.size(280.dp),
                            contentScale = ContentScale.Fit
                        )
                        Image(
                            painter = painterResource(id = R.drawable.engmu_shadow),
                            contentDescription = null,
                            modifier = Modifier.width(170.dp).height(16.dp),
                            contentScale = ContentScale.FillBounds
                        )
                    }
                }
            }
        }

        // 티어명 + 리그 정보 (간결)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFf1f8ff))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .graphicsLayer { alpha = tierNameAlpha.value },
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    tierDisplayName(uiState.tier),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF362000)
                )
                if (uiState.promotionCount > 0) {
                    Text(
                        "상위 ${uiState.promotionCount}명은 다음 리그로 승급합니다!",
                        fontSize = 13.sp,
                        color = Color(0xFF888888)
                    )
                }
                if (uiState.daysUntilReset > 0 || uiState.hoursUntilReset > 0) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("리그 종료까지", fontSize = 13.sp, color = Color(0xFF888888))
                        Text(
                            if (uiState.daysUntilReset > 0)
                                "${uiState.daysUntilReset}일 ${uiState.hoursUntilReset}시간 ${uiState.minutesUntilReset}분"
                            else
                                "${uiState.hoursUntilReset}시간 ${uiState.minutesUntilReset}분",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF5D5D)
                        )
                    }
                }
            }
        }

        // 미참여 / frozen
        if (!isParticipating) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 0.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        if (uiState.frozen) "4주 연속 미플레이로\n리더보드에 접근할 수 없어요."
                        else "잉무를 꼬시고 리그에 참여해 보세요!",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF362000),
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = { viewModel.onEvent(LeaderboardEvent.ClickPlay) },
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        shape = RoundedCornerShape(20),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEDF57))
                    ) {
                        Text("따라 말하기 도전하고 잉무 꼬시기", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF362000))
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
            items(6) { BlurredRankerRow() }
            item { Spacer(modifier = Modifier.height(32.dp)) }
            return@LazyColumn
        }

        // 구분선
        item {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp), color = Color(0xFFDDDDDD))
        }

        // TOP 랭킹 헤더
        item(key = "top_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("TOP 랭킹", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF888888))
                if (uiState.promotionCount > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFFFEDF57).copy(alpha = 0.3f))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text("🏆 상위 ${uiState.promotionCount}명 승급", fontSize = 11.sp, color = Color(0xFF362000), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        val myRank = uiState.nearbyRankers.find { it.isMe }?.rank ?: Int.MAX_VALUE
        val isMyRankInTop = myRank <= 5

        // TOP 3
        itemsIndexed(uiState.topRankers, key = { _, r -> "top_${r.rank}" }) { _, ranker ->
            RankerRow(ranker = ranker, showMedal = true, zoneStatus = getZoneStatus(ranker.rank, totalRankers))
        }

        if (isMyRankInTop) {
            // TOP5 이내 → 바로 이어서
            val topMaxRank = uiState.topRankers.lastOrNull()?.rank ?: 0
            itemsIndexed(
                uiState.nearbyRankers.filter { it.rank > topMaxRank },
                key = { _, r -> "nearby_top_${r.rank}" }
            ) { index, ranker ->
                val zone = getZoneStatus(ranker.rank, totalRankers)
                val adjustedIndex = uiState.nearbyRankers.indexOfFirst { it.rank == ranker.rank }
                if (adjustedIndex == myNearbyIndex) {
                    ZoneBanner(index = adjustedIndex, myNearbyIndex = myNearbyIndex, zone = zone)
                }
                RankerRow(ranker = ranker, showMedal = false, zoneStatus = zone)
            }
        } else {
            // TOP5 밖 → ⋮ 구분 후 nearbyRankers
            item(key = "separator") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("⋮", fontSize = 18.sp, color = Color(0xFFCCCCCC))
                }
            }

            itemsIndexed(uiState.nearbyRankers, key = { _, r -> "nearby_${r.rank}" }) { index, ranker ->
                val zone = getZoneStatus(ranker.rank, totalRankers)
                if (index == myNearbyIndex) {
                    ZoneBanner(index = index, myNearbyIndex = myNearbyIndex, zone = zone)
                }
                RankerRow(ranker = ranker, showMedal = false, zoneStatus = zone)
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
private fun RankerRow(
    ranker: Ranker,
    showMedal: Boolean,
    zoneStatus: ZoneStatus = ZoneStatus.SAFE
) {
    // 점수 카운팅 (내 순위만)
    val scoreAnim = remember { Animatable(0f) }
    LaunchedEffect(ranker.isMe) {
        if (ranker.isMe) {
            kotlinx.coroutines.delay(300)
            scoreAnim.animateTo(
                ranker.weeklyScore.toFloat(),
                animationSpec = tween(1000, easing = FastOutSlowInEasing)
            )
        }
    }

    // 내 순위 펄스
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.02f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "pulseScale"
    )

    val bgColor = when {
        ranker.isMe -> Color(0xFFFEDF57)
        zoneStatus == ZoneStatus.PROMOTION -> Color(0xFFFFFDE7)
        zoneStatus == ZoneStatus.DEMOTION -> Color(0xFFFFF5F5)
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // .then(if (ranker.isMe) Modifier.scale(pulseScale) else Modifier)
            .background(bgColor)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.Center) {
            if (showMedal) {
                RankBadge(ranker.rank)
            } else {
                Text(
                    "${ranker.rank}",
                    fontSize = 15.sp,
                    fontWeight = if (ranker.isMe) FontWeight.ExtraBold else FontWeight.Normal,
                    color = Color(0xFF362000),
                    textAlign = TextAlign.Center
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                ranker.nickname,
                fontSize = 15.sp,
                fontWeight = if (ranker.isMe) FontWeight.ExtraBold else FontWeight.Normal,
                color = Color(0xFF362000)
            )
            if (ranker.isMe) {
                Text("나", fontSize = 11.sp, color = Color(0xFF888888))
            }
        }

        Text(
            if (ranker.isMe) "%.0f점".format(scoreAnim.value)
            else "%.0f점".format(ranker.weeklyScore),
            fontSize = 15.sp,
            color = if (ranker.isMe) Color(0xFF362000) else Color(0xFF888888),
            fontWeight = if (ranker.isMe) FontWeight.ExtraBold else FontWeight.Normal
        )
    }
}

@Composable
private fun BlurredRankerRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(50)).background(Color(0xFFDDDDDD)))
        Box(modifier = Modifier.width(100.dp).height(14.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFDDDDDD)))
        Spacer(modifier = Modifier.weight(1f))
        Box(modifier = Modifier.width(60.dp).height(14.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFDDDDDD)))
    }
}

@Composable
private fun RankBadge(rank: Int) {
    when (rank) {
        1 -> Image(painter = painterResource(id = R.drawable.medal_gold), contentDescription = null, modifier = Modifier.size(26.dp))
        2 -> Image(painter = painterResource(id = R.drawable.medal_silver), contentDescription = null, modifier = Modifier.size(26.dp))
        3 -> Image(painter = painterResource(id = R.drawable.medal_bronze), contentDescription = null, modifier = Modifier.size(26.dp))
        else -> Text("$rank", fontSize = 15.sp, color = Color(0xFF362000), textAlign = TextAlign.Center)
    }
}

private fun tierDisplayName(tier: String) = when (tier) {
    "BRONZE"     -> "브론즈 리그"
    "SILVER"     -> "실버 리그"
    "GOLD"       -> "골드 리그"
    "PLATINUM"   -> "플래티넘 리그"
    "DIAMOND"    -> "다이아몬드 리그"
    "RUBY"       -> "루비 리그"
    "CHALLENGER" -> "챌린저 리그"
    else         -> ""
}

private val TIER_ORDER = listOf("BRONZE", "SILVER", "GOLD", "PLATINUM", "DIAMOND", "RUBY", "CHALLENGER")

private fun prevTier(tier: String?): String? {
    val idx = TIER_ORDER.indexOf(tier)
    return if (idx > 0) TIER_ORDER[idx - 1] else null
}

private fun nextTier(tier: String?): String? {
    val idx = TIER_ORDER.indexOf(tier ?: "")
    return if (idx >= 0 && idx < TIER_ORDER.size - 1) TIER_ORDER[idx + 1] else null
}

@Composable
private fun ZoneBanner(index: Int, myNearbyIndex: Int, zone: ZoneStatus) {
    if (index != myNearbyIndex) return
    when (zone) {
        ZoneStatus.PROMOTION -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFFFFF3CD))
                .padding(horizontal = 12.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("⬆️", fontSize = 12.sp)
            Text("승급 구간이에요!", fontSize = 12.sp, color = Color(0xFF856404), fontWeight = FontWeight.Bold)
        }
        ZoneStatus.DEMOTION -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFFFFF0F0))
                .padding(horizontal = 12.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("⚠️", fontSize = 12.sp)
            Text("강등 위험 구간이에요", fontSize = 12.sp, color = Color(0xFFCC0000), fontWeight = FontWeight.Bold)
        }
        ZoneStatus.SAFE -> {}
    }
}