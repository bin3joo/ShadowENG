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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import com.bremenband.shadoweng.core.ui.component.ThumbnailImage
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

    val isBookmarkLoading = bookmarkState.isLoading
    val inProgressSessions = sessionState.activeSessions
    val completedSessions = sessionState.completedSessions

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var deleteTargetId by remember { mutableStateOf<Long?>(null) }
    val tabs = listOf("학습하기", "복습하기", "즐겨찾기")

    if (deleteTargetId != null) {
        AlertDialog(
            onDismissRequest = { deleteTargetId = null },
            title = { Text("학습 삭제", fontWeight = FontWeight.Bold, color = Color(0xFF362000)) },
            text = { Text("이 학습 세션을 삭제하시겠어요?\n삭제 후 복구할 수 없어요.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteTargetId?.let {
                        sessionListViewModel.deleteSession(it)
                        bookmarkViewModel.reload()
                    }
                    deleteTargetId = null
                }) {
                    Text("삭제", color = Color(0xFFFF5D5D), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargetId = null }) {
                    Text("취소", color = Color(0xFF888888))
                }
            }
        )
    }

    LaunchedEffect(Unit) { viewModel.navigateToStudy.collect { onNavigateToStudy(it) } }
    LaunchedEffect(Unit) { viewModel.navigateToRegister.collect { onNavigateToRegister() } }
    LaunchedEffect(Unit) { viewModel.navigateToGame.collect { onNavigateToGame() } }
    LaunchedEffect(Unit) { sessionListViewModel.navigateToStudy.collect { onNavigateToStudy(it) } }
    LaunchedEffect(Unit) { bookmarkViewModel.navigateToStudy.collect { onNavigateToStudy(it) } }

    LaunchedEffect(Unit) {
        viewModel.reload()
        sessionListViewModel.reload()
        bookmarkViewModel.reload()
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
            Text("마이 쉐도잉", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF362000))
        }

        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = Color.White,
            contentColor = Color(0xFF362000),
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                    color = Color(0xFF362000)
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Text(
                            title,
                            fontSize = 14.sp,
                            fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTabIndex == index) Color(0xFF362000) else Color(0xFF888888)
                        )
                    }
                )
            }
        }

        when (selectedTabIndex) {
            0 -> ActiveSessionsTab(
                isLoading = sessionState.isLoading,
                sessions = inProgressSessions,
                onNavigateToStudy = onNavigateToStudy,
                onNavigateToRegister = onNavigateToRegister,
                onMoreClick = onNavigateToActiveSessions,
                onDelete = { deleteTargetId = it },
                onReview = { sessionListViewModel.startReview(it) },
                onReLearn = { sessionListViewModel.reLearn(it) },
                onBookmark = {
                    sessionListViewModel.toggleSessionBookmark(it) {
                        bookmarkViewModel.reload()
                    }
                }
            )
            1 -> CompletedSessionsTab(
                isLoading = sessionState.isLoading,
                sessions = completedSessions,
                onNavigateToStudy = onNavigateToStudy,
                onMoreClick = onNavigateToCompletedSessions,
                onDelete = { deleteTargetId = it },
                onReview = { sessionListViewModel.startReview(it) },
                onReLearn = { sessionListViewModel.reLearn(it) },
                onBookmark = {
                    sessionListViewModel.toggleSessionBookmark(it) {
                        bookmarkViewModel.reload()
                    }
                }
            )
            2 -> BookmarkTab(
                isLoading = bookmarkState.isLoading,
                sessions = bookmarkState.bookmarkedSessions,
                onRemove = { bookmarkViewModel.toggleBookmark(it) },
                onNavigateToStudy = { bookmarkViewModel.navigateToSession(it) }

            )
        }
    }
}

@Composable
private fun ActiveSessionsTab(
    isLoading: Boolean,
    sessions: List<SessionSummary>,
    onNavigateToStudy: (Long) -> Unit,
    onNavigateToRegister: () -> Unit,
    onMoreClick: () -> Unit,
    onDelete: (Long) -> Unit,
    onReview: (Long) -> Unit,
    onReLearn: (Long) -> Unit,
    onBookmark: (Long) -> Unit,
) {
    if (isLoading) { LoadingBox(); return }
    if (sessions.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("진행 중인 학습이 없어요", fontSize = 15.sp, color = Color(0xFF888888))
                Button(
                    onClick = onNavigateToRegister,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEDF57))
                ) {
                    Text("새 학습 시작하기", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF362000))
                }
            }
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(sessions.take(10), key = { it.sessionId }) { session ->
            SessionVerticalCard(
                session = session,
                onClick = { onNavigateToStudy(session.sessionId) },
                onDelete = { onDelete(session.sessionId) },
                onReview = { onReview(session.sessionId) },
                onReLearn = { onReLearn(session.sessionId) },
                onBookmark = { onBookmark(session.sessionId) }
            )
        }
        if (sessions.size > 10) {
            item {
                TextButton(onClick = onMoreClick, modifier = Modifier.fillMaxWidth()) {
                    Text("더보기", color = Color(0xFF888888))
                }
            }
        }
    }
}

@Composable
private fun CompletedSessionsTab(
    isLoading: Boolean,
    sessions: List<SessionSummary>,
    onNavigateToStudy: (Long) -> Unit,
    onMoreClick: () -> Unit,
    onDelete: (Long) -> Unit,
    onReview: (Long) -> Unit,
    onReLearn: (Long) -> Unit,
    onBookmark: (Long) -> Unit,
) {
    if (isLoading) { LoadingBox(); return }
    if (sessions.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("완료한 학습이 없어요", fontSize = 15.sp, color = Color(0xFF888888))
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(sessions.take(10), key = { it.sessionId }) { session ->
            SessionVerticalCard(
                session = session,
                onClick = { onNavigateToStudy(session.sessionId) },
                onDelete = { onDelete(session.sessionId) },
                onReview = { onReview(session.sessionId) },
                onReLearn = { onReLearn(session.sessionId) },
                onBookmark = { onBookmark(session.sessionId) }
            )
        }
        if (sessions.size > 10) {
            item {
                TextButton(onClick = onMoreClick, modifier = Modifier.fillMaxWidth()) {
                    Text("더보기", color = Color(0xFF888888))
                }
            }
        }
    }
}

@Composable
private fun BookmarkTab(
    isLoading: Boolean,
    sessions: List<SessionSummary>,
    onRemove: (Long) -> Unit,
    onNavigateToStudy: (Long) -> Unit
) {
    if (isLoading) { LoadingBox(); return }
    if (sessions.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("즐겨찾기한 세션이 없어요", fontSize = 15.sp, color = Color(0xFF888888))
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(sessions, key = { it.sessionId }) { session ->
            SessionVerticalCard(
                session = session,
                onClick = { onNavigateToStudy(session.sessionId) },
                onDelete = { onRemove(session.sessionId) },
                onReview = { onNavigateToStudy(session.sessionId) },
                onReLearn = { },
                onBookmark = { onRemove(session.sessionId) }
            )
        }
    }
}

@Composable
private fun SessionVerticalCard(
    session: SessionSummary,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onReview: () -> Unit,
    onReLearn: () -> Unit,
    onBookmark: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable {
            if (session.isCompleted) onReview() else onClick()
        },
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
                ThumbnailImage(
                    url = session.thumbnailUrl,
                    modifier = Modifier.width(100.dp).aspectRatio(16f / 9f)
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
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF362000),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
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
                    Text("${session.completedSentences}/${session.totalSentences}", fontSize = 10.sp, color = Color(0xFF888888))
                }
            }

            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "더보기", tint = Color(0xFFBBBBBB), modifier = Modifier.size(18.dp))
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.width(160.dp),
                    shape = RoundedCornerShape(16.dp),
                    containerColor = Color.White,
                    shadowElevation = 4.dp
                ) {
                    DropdownMenuItem(
                        text = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (session.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = null,
                                    tint = if (session.isBookmarked) Color(0xFFFF5D5D) else Color(0xFF362000),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    if (session.isBookmarked) "즐겨찾기 해제" else "즐겨찾기 추가",
                                    fontSize = 14.sp,
                                    color = Color(0xFF362000),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        },
                        onClick = { showMenu = false; onBookmark() },
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    if (session.isCompleted) {
                        HorizontalDivider(color = Color(0xFFF0F0F0), modifier = Modifier.padding(horizontal = 12.dp))
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "재학습하기",
                                    fontSize = 14.sp,
                                    color = Color(0xFF362000),
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            onClick = { showMenu = false; onReLearn() },
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                    HorizontalDivider(color = Color(0xFFF0F0F0), modifier = Modifier.padding(horizontal = 12.dp))
                    DropdownMenuItem(
                        text = {
                            Text(
                                "삭제",
                                fontSize = 14.sp,
                                color = Color(0xFFFF5D5D),
                                fontWeight = FontWeight.Medium
                            )
                        },
                        onClick = { showMenu = false; onDelete() },
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BookmarkCard(bookmark: BookmarkedSentence, onRemove: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                bookmark.sentence,
                fontSize = 14.sp, color = Color(0xFF1A1A1A), lineHeight = 22.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                Icon(
                    Icons.Default.Bookmark,
                    contentDescription = "즐겨찾기 해제",
                    tint = Color(0xFFFF5D5D),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun LoadingBox() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color(0xFFFEDF57))
    }
}