package com.bremenband.shadoweng.feature.mypage.presentation.mypage

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.bremenband.shadoweng.R
import com.bremenband.shadoweng.core.ui.util.tierToDrawable
import com.bremenband.shadoweng.feature.mypage.domain.model.BookmarkedSentence
import com.bremenband.shadoweng.feature.mypage.domain.model.SessionSummary
import com.bremenband.shadoweng.feature.mypage.presentation.bookmark.BookmarkViewModel
import com.bremenband.shadoweng.feature.mypage.presentation.sessions.SessionListViewModel

@Composable
fun MyPageScreen(
    onNavigateToStudy: (sessionId: Long) -> Unit,
    onNavigateToRegister: () -> Unit = {},
    onNavigateToGame: () -> Unit = {},
    onNavigateToBookmark: () -> Unit = {},
    onNavigateToActiveSessions: () -> Unit = {},
    onNavigateToCompletedSessions: () -> Unit = {},
    viewModel: MyPageViewModel = hiltViewModel(),
    sessionListViewModel: SessionListViewModel = hiltViewModel(),
    bookmarkViewModel: BookmarkViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val sessionState by sessionListViewModel.state.collectAsState()
    val bookmarkState by bookmarkViewModel.state.collectAsState()

    val bookmarks = bookmarkState.bookmarks
    val isBookmarkLoading = bookmarkState.isLoading
    val inProgressSessions = sessionState.activeSessions
    val completedSessions = sessionState.completedSessions

    LaunchedEffect(Unit) { viewModel.navigateToStudy.collect { onNavigateToStudy(it) } }
    LaunchedEffect(Unit) { viewModel.navigateToRegister.collect { onNavigateToRegister() } }
    LaunchedEffect(Unit) { viewModel.navigateToGame.collect { onNavigateToGame() } }

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
            Text("학습", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF362000))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 이어하기
            item {
                SectionHeader(title = "이어하기", onMoreClick = onNavigateToActiveSessions)
                Spacer(modifier = Modifier.height(8.dp))
                if (sessionState.isLoading) {
                    LoadingRow()
                } else if (inProgressSessions.isEmpty()) {
                    EmptyHorizontalCard(
                        message = "진행 중인 학습이 없어요",
                        actionLabel = "새 학습 시작하기",
                        onClick = onNavigateToRegister
                    )
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(inProgressSessions.take(3), key = { it.sessionId }) { session ->
                            SessionHorizontalCard(
                                session = session,
                                onClick = { onNavigateToStudy(session.sessionId) }
                            )
                        }
                    }
                }
            }

            // 복습하기
            item {
                SectionHeader(title = "복습하기", onMoreClick = onNavigateToCompletedSessions)
                Spacer(modifier = Modifier.height(8.dp))
                if (sessionState.isLoading) {
                    LoadingRow()
                } else if (completedSessions.isEmpty()) {
                    EmptyHorizontalCard(
                        message = "완료한 학습이 없어요",
                        actionLabel = null,
                        onClick = null
                    )
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(completedSessions.take(3), key = { it.sessionId }) { session ->
                            SessionHorizontalCard(
                                session = session,
                                onClick = { onNavigateToStudy(session.sessionId) }
                            )
                        }
                    }
                }
            }

            // 게임 섹션
            item {
                SectionHeader(title = "잉무를 꼬셔라", onMoreClick = null)
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    HubGameCard(
                        onClick = onNavigateToGame,
                        leagueTopScore = state.leagueTopScore,
                        myScore = state.myScore,
                        tier = state.tier
                    )
                }
            }

            // 북마크 헤더
            item {
                SectionHeader(title = "북마크", onMoreClick = onNavigateToBookmark)
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 북마크 콘텐츠
            item(key = "bookmark_content_${bookmarks.size}_$isBookmarkLoading") {
                when {
                    isBookmarkLoading -> LoadingRow()
                    bookmarks.isEmpty() -> EmptyHorizontalCard(
                        message = "북마크한 문장이 없어요",
                        actionLabel = null,
                        onClick = null
                    )
                    else -> {
                        val displayBookmarks = bookmarks.take(3)
                        val hasMore = bookmarks.size > 3
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(displayBookmarks, key = { it.sentenceId }) { bookmark ->
                                BookmarkHorizontalCard(
                                    bookmark = bookmark,
                                    onRemove = { bookmarkViewModel.toggleBookmark(bookmark.sentenceId) }
                                )
                            }
                            if (hasMore) {
                                item {
                                    Card(
                                        modifier = Modifier
                                            .width(120.dp)
                                            .height(130.dp)
                                            .clickable { onNavigateToBookmark() },
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F3)),
                                        elevation = CardDefaults.cardElevation(0.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    "+ ${bookmarks.size - 3}",
                                                    fontSize = 18.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Color(0xFF362000)
                                                )
                                                Text("더 보기", fontSize = 12.sp, color = Color(0xFF888888))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, onMoreClick: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF362000))
        if (onMoreClick != null) {
            Row(
                modifier = Modifier.clickable { onMoreClick() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text("더보기", fontSize = 12.sp, color = Color(0xFF888888))
                Icon(
                    Icons.Default.ArrowForwardIos,
                    contentDescription = null,
                    tint = Color(0xFFBBBBBB),
                    modifier = Modifier.size(10.dp)
                )
            }
        }
    }
}

@Composable
private fun LoadingRow() {
    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color(0xFFFEDF57))
    }
}

@Composable
private fun EmptyHorizontalCard(message: String, actionLabel: String?, onClick: (() -> Unit)?) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(message, fontSize = 14.sp, color = Color(0xFF888888))
            if (actionLabel != null && onClick != null) {
                Button(
                    onClick = onClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEDF57))
                ) {
                    Text(actionLabel, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF362000))
                }
            }
        }
    }
}

@Composable
private fun SessionHorizontalCard(session: SessionSummary, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(300.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                AsyncImage(
                    model = session.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(R.drawable.thumbnail),
                    error = painterResource(R.drawable.thumbnail),
                    modifier = Modifier
                        .width(100.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(10.dp))
                )
                if (session.isCompleted) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF362000).copy(alpha = 0.75f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("완료", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    session.title.ifEmpty { "학습 세션" },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF362000),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { session.progressRate / 100f },
                        modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(4.dp)),
                        color = if (session.isCompleted) Color(0xFFFF5D5D) else Color(0xFFFEDF57),
                        trackColor = Color(0xFFE0E0E0)
                    )
                    Text(
                        "${session.completedSentences}/${session.totalSentences}",
                        fontSize = 10.sp, color = Color(0xFF888888)
                    )
                }
                OutlinedButton(
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth().height(28.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    border = BorderStroke(
                        1.dp,
                        if (session.isCompleted) Color(0xFFDDDDDD) else Color(0xFFFEDF57)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (session.isCompleted) Color(0xFF888888) else Color(0xFF362000)
                    )
                ) {
                    Text(
                        if (session.isCompleted) "복습하기" else "이어서 학습하기",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (session.isCompleted) Color(0xFF888888) else Color(0xFF362000)
                    )
                }
            }
        }
    }
}

@Composable
private fun BookmarkHorizontalCard(bookmark: BookmarkedSentence, onRemove: () -> Unit) {
    Card(
        modifier = Modifier.width(240.dp).height(130.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                bookmark.sentence,
                fontSize = 14.sp, color = Color(0xFF362000), lineHeight = 22.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Default.Bookmark,
                        contentDescription = "북마크 해제",
                        tint = Color(0xFFFF5D5D),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HubGameCard(onClick: () -> Unit, leagueTopScore: Int, myScore: Int, tier: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(140.dp)) {
            Image(
                painter = painterResource(R.drawable.background3),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Image(
                    painter = painterResource(tierToDrawable(tier)),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxHeight().weight(1f)
                )
                Column(
                    modifier = Modifier.weight(1.5f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("리그 최고", fontSize = 10.sp, color = Color(0xFF888888))
                                Text("$leagueTopScore", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFEDF57))
                            }
                            Box(modifier = Modifier.width(1.dp).height(40.dp).align(Alignment.CenterVertically).background(Color(0xFFE0E0E0)))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("내 기록", fontSize = 10.sp, color = Color(0xFF888888))
                                Text("$myScore", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFF5D5D))
                            }
                        }
                    }
                    Button(
                        onClick = onClick,
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEDF57))
                    ) {
                        Text("잉무 꼬시러 가기", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF362000))
                    }
                }
            }
        }
    }
}