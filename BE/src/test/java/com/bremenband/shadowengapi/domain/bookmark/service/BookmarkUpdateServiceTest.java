package com.bremenband.shadowengapi.domain.bookmark.service;

import com.bremenband.shadowengapi.domain.bookmark.dto.res.BookmarkUpdateResponse;
import com.bremenband.shadowengapi.domain.bookmark.entity.Bookmark;
import com.bremenband.shadowengapi.domain.bookmark.repository.BookmarkRepository;
import com.bremenband.shadowengapi.domain.study.entity.Sentence;
import com.bremenband.shadowengapi.domain.study.entity.StudySession;
import com.bremenband.shadowengapi.domain.youtube.entity.Video;
import com.bremenband.shadowengapi.domain.study.repository.SentenceRepository;
import com.bremenband.shadowengapi.domain.user.entity.User;
import com.bremenband.shadowengapi.domain.user.repository.UserRepository;
import com.bremenband.shadowengapi.global.exception.CustomException;
import com.bremenband.shadowengapi.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class BookmarkUpdateServiceTest {

    @InjectMocks private BookmarkService bookmarkService;

    @Mock private BookmarkRepository bookmarkRepository;
    @Mock private SentenceRepository  sentenceRepository;
    @Mock private UserRepository      userRepository;

    // ── 헬퍼 ────────────────────────────────────────────────────────────────────

    private User buildUser(Long userId) {
        User user = User.builder()
                .email("u@e.com").nickname("nick").provider("KAKAO").providerId("p").build();
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private Sentence buildSentence(Long sentenceId, String content) {
        Video video = Video.builder()
                .videoId("vid").title("T").embedUrl("e").thumbnailUrl("th").duration(100).channelTitle("Ch").build();
        User user = buildUser(1L);
        StudySession session = StudySession.builder()
                .video(video).user(user).startSec(0).endSec(60).build();
        ReflectionTestUtils.setField(session, "id", 10L);

        Sentence sentence = Sentence.builder()
                .studySession(session).content(content).startSec(0).endSec(5).durationSec(5).build();
        ReflectionTestUtils.setField(sentence, "id", sentenceId);
        return sentence;
    }

    // ── 테스트 케이스 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("isBookmarked=true이고 북마크가 없으면 북마크를 생성하고 true를 반환한다")
    void updateBookmark_설정_북마크없음_생성() {
        // given
        Long userId = 1L;
        Long sentenceId = 10L;
        User user = buildUser(userId);
        Sentence sentence = buildSentence(sentenceId, "I got it bad.");

        given(sentenceRepository.findById(sentenceId)).willReturn(Optional.of(sentence));
        given(bookmarkRepository.findByUser_IdAndSentence_Id(userId, sentenceId)).willReturn(Optional.empty());
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        // when
        BookmarkUpdateResponse response = bookmarkService.updateBookmark(userId, sentenceId, true);

        // then
        assertThat(response.sentenceId()).isEqualTo(sentenceId);
        assertThat(response.sentence()).isEqualTo("I got it bad.");
        assertThat(response.isBookmarked()).isTrue();

        then(bookmarkRepository).should(times(1)).save(any(Bookmark.class));
    }

    @Test
    @DisplayName("isBookmarked=true이고 이미 북마크가 있으면 중복 저장하지 않는다")
    void updateBookmark_설정_북마크이미존재_저장안함() {
        // given
        Long userId = 1L;
        Long sentenceId = 10L;
        User user = buildUser(userId);
        Sentence sentence = buildSentence(sentenceId, "I got it bad.");
        Bookmark existing = Bookmark.builder().user(user).sentence(sentence).build();

        given(sentenceRepository.findById(sentenceId)).willReturn(Optional.of(sentence));
        given(bookmarkRepository.findByUser_IdAndSentence_Id(userId, sentenceId)).willReturn(Optional.of(existing));

        // when
        BookmarkUpdateResponse response = bookmarkService.updateBookmark(userId, sentenceId, true);

        // then
        assertThat(response.isBookmarked()).isTrue();
        then(bookmarkRepository).should(never()).save(any());
        then(userRepository).should(never()).findById(any());
    }

    @Test
    @DisplayName("isBookmarked=false이면 북마크를 삭제하고 false를 반환한다")
    void updateBookmark_해제_북마크삭제() {
        // given
        Long userId = 1L;
        Long sentenceId = 10L;
        Sentence sentence = buildSentence(sentenceId, "I got it bad.");

        given(sentenceRepository.findById(sentenceId)).willReturn(Optional.of(sentence));

        // when
        BookmarkUpdateResponse response = bookmarkService.updateBookmark(userId, sentenceId, false);

        // then
        assertThat(response.sentenceId()).isEqualTo(sentenceId);
        assertThat(response.sentence()).isEqualTo("I got it bad.");
        assertThat(response.isBookmarked()).isFalse();

        then(bookmarkRepository).should(times(1)).deleteByUser_IdAndSentence_Id(userId, sentenceId);
        then(bookmarkRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("존재하지 않는 문장이면 SENTENCE_NOT_FOUND 예외를 던진다")
    void updateBookmark_문장없음_예외() {
        // given
        Long userId = 1L;
        Long sentenceId = 999L;

        given(sentenceRepository.findById(sentenceId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> bookmarkService.updateBookmark(userId, sentenceId, true))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SENTENCE_NOT_FOUND);

        then(bookmarkRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("북마크 설정 시 사용자가 없으면 USER_NOT_FOUND 예외를 던진다")
    void updateBookmark_설정_사용자없음_예외() {
        // given
        Long userId = 999L;
        Long sentenceId = 10L;
        Sentence sentence = buildSentence(sentenceId, "I got it bad.");

        given(sentenceRepository.findById(sentenceId)).willReturn(Optional.of(sentence));
        given(bookmarkRepository.findByUser_IdAndSentence_Id(userId, sentenceId)).willReturn(Optional.empty());
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> bookmarkService.updateBookmark(userId, sentenceId, true))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }
}
