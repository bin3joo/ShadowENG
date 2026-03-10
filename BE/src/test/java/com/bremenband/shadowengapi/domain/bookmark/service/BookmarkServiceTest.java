package com.bremenband.shadowengapi.domain.bookmark.service;

import com.bremenband.shadowengapi.domain.bookmark.dto.res.BookmarkListResponse;
import com.bremenband.shadowengapi.domain.bookmark.entity.Bookmark;
import com.bremenband.shadowengapi.domain.bookmark.repository.BookmarkRepository;
import com.bremenband.shadowengapi.domain.study.entity.Sentence;
import com.bremenband.shadowengapi.domain.study.entity.StudySession;
import com.bremenband.shadowengapi.domain.youtube.entity.Video;
import com.bremenband.shadowengapi.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class BookmarkServiceTest {

    @InjectMocks private BookmarkService bookmarkService;

    @Mock private BookmarkRepository bookmarkRepository;

    // ── 헬퍼 ────────────────────────────────────────────────────────────────────

    private User buildUser(Long userId) {
        User user = User.builder()
                .email("u@e.com").nickname("nick").provider("KAKAO").providerId("p").build();
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private StudySession buildSession(Long sessionId, User user) {
        Video video = Video.builder()
                .videoId("dQw4w9WgXcQ").title("T").embedUrl("e")
                .thumbnailUrl("th").duration(212).channelTitle("Ch").build();
        StudySession session = StudySession.builder()
                .video(video).user(user).startSec(0).endSec(60).build();
        ReflectionTestUtils.setField(session, "id", sessionId);
        return session;
    }

    private Sentence buildSentence(Long sentenceId, StudySession session, String content) {
        Sentence sentence = Sentence.builder()
                .studySession(session).content(content)
                .startSec(0).endSec(5).durationSec(5).build();
        ReflectionTestUtils.setField(sentence, "id", sentenceId);
        return sentence;
    }

    private Bookmark buildBookmark(Long bookmarkId, User user, Sentence sentence) {
        Bookmark bookmark = Bookmark.builder().user(user).sentence(sentence).build();
        ReflectionTestUtils.setField(bookmark, "id", bookmarkId);
        return bookmark;
    }

    // ── 테스트 케이스 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("북마크가 있으면 sentenceId, sentence, sessionId를 포함한 목록을 반환한다")
    void getBookmarks_북마크존재_목록반환() {
        // given
        Long userId = 1L;
        User user = buildUser(userId);
        StudySession session = buildSession(100L, user);
        Sentence s1 = buildSentence(10L, session, "I got it bad.");
        Sentence s2 = buildSentence(11L, session, "She's got it good.");
        Bookmark b1 = buildBookmark(1L, user, s1);
        Bookmark b2 = buildBookmark(2L, user, s2);

        given(bookmarkRepository.findByUser_IdOrderByCreatedAtDesc(userId))
                .willReturn(List.of(b1, b2));

        // when
        BookmarkListResponse response = bookmarkService.getBookmarks(userId);

        // then
        assertThat(response.bookmarks()).hasSize(2);

        BookmarkListResponse.BookmarkItem item1 = response.bookmarks().get(0);
        assertThat(item1.sentenceId()).isEqualTo(10L);
        assertThat(item1.sentence()).isEqualTo("I got it bad.");
        assertThat(item1.sessionId()).isEqualTo(100L);

        BookmarkListResponse.BookmarkItem item2 = response.bookmarks().get(1);
        assertThat(item2.sentenceId()).isEqualTo(11L);
        assertThat(item2.sentence()).isEqualTo("She's got it good.");
        assertThat(item2.sessionId()).isEqualTo(100L);

        then(bookmarkRepository).should(times(1)).findByUser_IdOrderByCreatedAtDesc(userId);
    }

    @Test
    @DisplayName("북마크가 없으면 빈 목록을 반환한다")
    void getBookmarks_북마크없음_빈목록반환() {
        // given
        Long userId = 2L;

        given(bookmarkRepository.findByUser_IdOrderByCreatedAtDesc(userId))
                .willReturn(List.of());

        // when
        BookmarkListResponse response = bookmarkService.getBookmarks(userId);

        // then
        assertThat(response.bookmarks()).isEmpty();
        then(bookmarkRepository).should(times(1)).findByUser_IdOrderByCreatedAtDesc(userId);
    }

    @Test
    @DisplayName("여러 세션의 북마크도 올바르게 반환한다")
    void getBookmarks_여러세션_정상반환() {
        // given
        Long userId = 3L;
        User user = buildUser(userId);
        StudySession session1 = buildSession(200L, user);
        StudySession session2 = buildSession(201L, user);
        Sentence s1 = buildSentence(20L, session1, "Hello world.");
        Sentence s2 = buildSentence(21L, session2, "Goodbye world.");
        Bookmark b1 = buildBookmark(10L, user, s1);
        Bookmark b2 = buildBookmark(11L, user, s2);

        given(bookmarkRepository.findByUser_IdOrderByCreatedAtDesc(userId))
                .willReturn(List.of(b1, b2));

        // when
        BookmarkListResponse response = bookmarkService.getBookmarks(userId);

        // then
        assertThat(response.bookmarks()).hasSize(2);
        assertThat(response.bookmarks().get(0).sessionId()).isEqualTo(200L);
        assertThat(response.bookmarks().get(1).sessionId()).isEqualTo(201L);
    }
}
