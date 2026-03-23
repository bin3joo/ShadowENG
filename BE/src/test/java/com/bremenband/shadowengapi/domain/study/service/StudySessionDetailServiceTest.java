package com.bremenband.shadowengapi.domain.study.service;

import com.bremenband.shadowengapi.domain.study.dto.res.StudySessionCreateResponse;
import com.bremenband.shadowengapi.domain.study.entity.Evaluation;
import com.bremenband.shadowengapi.domain.study.entity.Sentence;
import com.bremenband.shadowengapi.domain.study.entity.StudySession;
import com.bremenband.shadowengapi.domain.youtube.entity.Video;
import com.bremenband.shadowengapi.domain.study.repository.EvaluationRepository;
import com.bremenband.shadowengapi.domain.study.repository.SentenceRepository;
import com.bremenband.shadowengapi.domain.study.repository.StudySessionRepository;
import com.bremenband.shadowengapi.domain.youtube.repository.VideoRepository;
import com.bremenband.shadowengapi.domain.user.entity.User;
import com.bremenband.shadowengapi.domain.user.repository.UserRepository;
import com.bremenband.shadowengapi.domain.youtube.service.YoutubeService;
import com.bremenband.shadowengapi.global.exception.CustomException;
import com.bremenband.shadowengapi.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class StudySessionDetailServiceTest {

    @InjectMocks private StudySessionService studySessionService;

    @Mock private StudySessionRepository studySessionRepository;
    @Mock private VideoRepository        videoRepository;
    @Mock private SentenceRepository     sentenceRepository;
    @Mock private EvaluationRepository   evaluationRepository;
    @Mock private UserRepository         userRepository;
    @Mock private YoutubeService         youtubeService;
    @Mock private TranscriptionService   transcriptionService;

    private static final String VIDEO_ID = "dQw4w9WgXcQ";

    // ── 헬퍼 ────────────────────────────────────────────────────────────────────

    private static final Long USER_ID = 1L;

    private StudySession buildSession(Long sessionId) {
        Video video = Video.builder()
                .videoId(VIDEO_ID)
                .title("Never Gonna Give You Up")
                .embedUrl("https://www.youtube.com/embed/" + VIDEO_ID)
                .thumbnailUrl("https://i.ytimg.com/vi/" + VIDEO_ID + "/maxresdefault.jpg")
                .duration(212)
                .channelTitle("Rick Astley")
                .build();

        User user = User.builder()
                .email("user@example.com").nickname("브레맨")
                .provider("KAKAO").providerId("kakao-123").build();
        ReflectionTestUtils.setField(user, "id", USER_ID);

        StudySession session = StudySession.builder()
                .video(video).user(user).startSec(10.0).endSec(30.0).build();
        ReflectionTestUtils.setField(session, "id", sessionId);
        return session;
    }

    private Sentence buildSentence(Long sentenceId, StudySession session,
                                   double start, double end, double duration) {
        Sentence sentence = Sentence.builder()
                .studySession(session)
                .content("I got it bad.")
                .startSec(start).endSec(end).durationSec(duration)
                .build();
        ReflectionTestUtils.setField(sentence, "id", sentenceId);
        return sentence;
    }

    private Evaluation buildEvaluation(Sentence sentence, StudySession session) {
        return Evaluation.builder()
                .studySession(session).sentence(sentence)
                .userTranscription("test")
                .wordLevelFeedback("[]").boundaryToneFeedback("{}").dynamicStressFeedback("{}")
                .totalScore(java.math.BigDecimal.valueOf(80.0))
                .wordAccuracy(java.math.BigDecimal.valueOf(80.0))
                .prosodyAndStress(java.math.BigDecimal.valueOf(80.0))
                .wordRhythmScore(java.math.BigDecimal.valueOf(80.0))
                .boundaryToneScore(java.math.BigDecimal.valueOf(80.0))
                .dynamicStressScore(java.math.BigDecimal.valueOf(80.0))
                .speedSimilarity(java.math.BigDecimal.valueOf(80.0))
                .pauseSimilarity(java.math.BigDecimal.valueOf(80.0))
                .build();
    }

    // ── 테스트 케이스 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("세션이 존재하면 영상 정보와 문장 목록(studyCount 포함)을 반환한다")
    void getStudySession_세션존재_상세정보반환() {
        // given
        Long sessionId = 1L;
        StudySession session = buildSession(sessionId);
        Sentence s1 = buildSentence(10L, session, 5.61, 10.78, 5.17);
        Sentence s2 = buildSentence(11L, session, 11.1, 15.83, 4.73);

        // 문장 10에 대한 studyCount=2, 문장 11에 대한 studyCount=0
        ReflectionTestUtils.setField(s1, "studyCount", 2);

        given(studySessionRepository.findByIdWithVideo(sessionId)).willReturn(Optional.of(session));
        given(sentenceRepository.findByStudySession_Id(sessionId)).willReturn(List.of(s1, s2));

        // when
        StudySessionCreateResponse response = studySessionService.getStudySession(sessionId, USER_ID);

        // then
        assertThat(response.sessionId()).isEqualTo(sessionId);

        // videoData 검증
        assertThat(response.videoData().videoId()).isEqualTo(VIDEO_ID);
        assertThat(response.videoData().title()).isEqualTo("Never Gonna Give You Up");
        assertThat(response.videoData().embedUrl())
                .isEqualTo("https://www.youtube.com/embed/" + VIDEO_ID);
        assertThat(response.videoData().duration()).isEqualTo(212L);
        assertThat(response.videoData().channelTitle()).isEqualTo("Rick Astley");

        // sentencesData 검증
        assertThat(response.sentencesData()).hasSize(2);

        assertThat(response.sentencesData().get(0).sentenceId()).isEqualTo(10L);
        assertThat(response.sentencesData().get(0).sentence()).isEqualTo("I got it bad.");
        assertThat(response.sentencesData().get(0).startSec()).isEqualTo(5.61);
        assertThat(response.sentencesData().get(0).endSec()).isEqualTo(10.78);
        assertThat(response.sentencesData().get(0).durationSec()).isEqualTo(5.17);
        assertThat(response.sentencesData().get(0).studyCount()).isEqualTo(2);

        assertThat(response.sentencesData().get(1).sentenceId()).isEqualTo(11L);
        assertThat(response.sentencesData().get(1).studyCount()).isEqualTo(0);

        then(studySessionRepository).should(times(1)).findByIdWithVideo(sessionId);
        then(sentenceRepository).should(times(1)).findByStudySession_Id(sessionId);
    }

    @Test
    @DisplayName("문장이 없는 세션도 빈 sentencesData로 정상 반환한다")
    void getStudySession_문장없는세션_빈목록반환() {
        // given
        Long sessionId = 1L;
        StudySession session = buildSession(sessionId);

        given(studySessionRepository.findByIdWithVideo(sessionId)).willReturn(Optional.of(session));
        given(sentenceRepository.findByStudySession_Id(sessionId)).willReturn(List.of());

        // when
        StudySessionCreateResponse response = studySessionService.getStudySession(sessionId, USER_ID);

        // then
        assertThat(response.sessionId()).isEqualTo(sessionId);
        assertThat(response.sentencesData()).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 sessionId면 SESSION_NOT_FOUND 예외를 던진다")
    void getStudySession_세션없음_예외() {
        // given
        Long sessionId = 999L;
        given(studySessionRepository.findByIdWithVideo(sessionId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> studySessionService.getStudySession(sessionId, USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SESSION_NOT_FOUND);

        then(sentenceRepository).should(times(0)).findByStudySession_Id(sessionId);
    }
}
