package com.bremenband.shadowengapi.domain.user.service;

import com.bremenband.shadowengapi.domain.report.entity.Report;
import com.bremenband.shadowengapi.domain.report.repository.ReportRepository;
import com.bremenband.shadowengapi.domain.study.entity.StudySession;
import com.bremenband.shadowengapi.domain.study.repository.EvaluationRepository;
import com.bremenband.shadowengapi.domain.study.repository.SentenceRepository;
import com.bremenband.shadowengapi.domain.user.dto.res.UserDashboardResponse;
import com.bremenband.shadowengapi.domain.user.dto.res.UserInfoResponse;
import com.bremenband.shadowengapi.domain.user.entity.AttendanceLog;
import com.bremenband.shadowengapi.domain.user.entity.User;
import com.bremenband.shadowengapi.domain.user.repository.AttendanceLogRepository;
import com.bremenband.shadowengapi.domain.user.repository.UserRepository;
import com.bremenband.shadowengapi.domain.youtube.entity.Video;
import com.bremenband.shadowengapi.global.exception.CustomException;
import com.bremenband.shadowengapi.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock private UserRepository          userRepository;
    @Mock private EvaluationRepository    evaluationRepository;
    @Mock private AttendanceLogRepository attendanceLogRepository;
    @Mock private SentenceRepository      sentenceRepository;
    @Mock private ReportRepository        reportRepository;

    @Test
    @DisplayName("존재하는 userId로 조회하면 사용자 정보를 반환한다")
    void getUserInfo_성공() {
        // given
        Long userId = 1L;
        User user = User.builder()
                .email("user@example.com")
                .nickname("브레맨")
                .provider("KAKAO")
                .providerId("kakao-123")
                .build();
        ReflectionTestUtils.setField(user, "id", userId);
        ReflectionTestUtils.setField(user, "visitedCount", 15);
        ReflectionTestUtils.setField(user, "createdAt", LocalDateTime.of(2025, 8, 1, 10, 0, 0));

        // 출석일: 8/1, 8/2 연속 → streak=2, 8/4 단절
        List<LocalDate> attendanceDates = List.of(
                LocalDate.of(2025, 8, 1),
                LocalDate.of(2025, 8, 2),
                LocalDate.of(2025, 8, 4)
        );
        // 학습일: 달력 표시용
        List<LocalDate> studyDates = List.of(
                LocalDate.of(2025, 8, 1),
                LocalDate.of(2025, 8, 2)
        );

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(attendanceLogRepository.findVisitDatesByUserId(userId)).willReturn(attendanceDates);
        given(evaluationRepository.findDistinctStudyDatesByUserId(userId)).willReturn(studyDates);

        // when
        UserInfoResponse response = userService.getUserInfo(userId);

        // then
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.nickname()).isEqualTo("브레맨");
        assertThat(response.email()).isEqualTo("user@example.com");
        assertThat(response.totalVisitedDays()).isEqualTo(15);
        assertThat(response.totalStudyDays()).isEqualTo(2);
        assertThat(response.longestStreak()).isEqualTo(2); // 출석일 기준: 8/1~8/2 연속
        assertThat(response.studyDates()).isEqualTo(studyDates);
        assertThat(response.createdAt()).isEqualTo(LocalDateTime.of(2025, 8, 1, 10, 0, 0));

        then(userRepository).should(times(1)).findById(userId);
        then(attendanceLogRepository).should(times(1)).findVisitDatesByUserId(userId);
        then(evaluationRepository).should(times(1)).findDistinctStudyDatesByUserId(userId);
    }

    @Test
    @DisplayName("존재하지 않는 userId로 조회하면 USER_NOT_FOUND 예외를 던진다")
    void getUserInfo_존재하지않는사용자_예외() {
        // given
        Long userId = 999L;

        given(userRepository.findById(userId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.getUserInfo(userId))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        then(userRepository).should(times(1)).findById(userId);
    }

    @Test
    @DisplayName("새로운 날 방문하면 visitedCount가 증가하고 AttendanceLog가 저장된다")
    void incrementVisitedCount_새날방문_출석로그저장() {
        // given
        Long userId = 1L;
        User user = User.builder()
                .email("user@example.com")
                .nickname("브레맨")
                .provider("KAKAO")
                .providerId("kakao-123")
                .build();
        ReflectionTestUtils.setField(user, "id", userId);
        // lastVisitedDate = null → 오늘과 다르므로 새 날로 처리
        ReflectionTestUtils.setField(user, "lastVisitedDate", null);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(attendanceLogRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        userService.incrementVisitedCount(userId);

        // then
        ArgumentCaptor<AttendanceLog> captor = ArgumentCaptor.forClass(AttendanceLog.class);
        then(attendanceLogRepository).should(times(1)).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getVisitDate()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("같은 날 재방문하면 AttendanceLog가 저장되지 않는다")
    void incrementVisitedCount_당일재방문_출석로그없음() {
        // given
        Long userId = 1L;
        User user = User.builder()
                .email("user@example.com")
                .nickname("브레맨")
                .provider("KAKAO")
                .providerId("kakao-123")
                .build();
        ReflectionTestUtils.setField(user, "id", userId);
        ReflectionTestUtils.setField(user, "lastVisitedDate", LocalDate.now()); // 이미 오늘 방문

        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        // when
        userService.incrementVisitedCount(userId);

        // then
        then(attendanceLogRepository).should(times(0)).save(any());
    }

    // ===== getDashboard 테스트 =====

    @Test
    @DisplayName("학습 이력이 있는 사용자는 출석/학습 통계와 레포트 날짜가 올바르게 반환된다")
    void getDashboard_정상시나리오_통계반환() {
        // given
        Long userId = 1L;

        List<LocalDate> attendanceDates = List.of(
                LocalDate.of(2025, 8, 1),
                LocalDate.of(2025, 8, 2),
                LocalDate.of(2025, 8, 3), // 3일 연속
                LocalDate.of(2025, 8, 5)  // 단절
        );
        List<LocalDate> studyDates = List.of(
                LocalDate.of(2025, 8, 1),
                LocalDate.of(2025, 8, 3)
        );

        Video video = buildVideo("dQw4w9WgXcQ", "Test Video", "https://thumb.jpg");
        User user   = buildUser(userId);
        Report report1 = buildReport(1L, buildSession(10L, user, video),
                LocalDateTime.of(2025, 8, 1, 10, 0));
        Report report2 = buildReport(2L, buildSession(11L, user, video),
                LocalDateTime.of(2025, 8, 1, 15, 0)); // 같은 날 두 번째 레포트
        Report report3 = buildReport(3L, buildSession(12L, user, video),
                LocalDateTime.of(2025, 8, 3, 9, 0));

        given(attendanceLogRepository.findVisitDatesByUserId(userId)).willReturn(attendanceDates);
        given(evaluationRepository.findDistinctStudyDatesByUserId(userId)).willReturn(studyDates);
        given(sentenceRepository.countStudiedSentencesByUserId(userId)).willReturn(15L);
        given(sentenceRepository.sumStudyTimeSecByUserId(userId)).willReturn(720.5);
        given(reportRepository.findAllByUserIdWithVideoOrderByCreatedAtAsc(userId))
                .willReturn(List.of(report1, report2, report3));

        // when
        UserDashboardResponse response = userService.getDashboard(userId);

        // then
        assertThat(response.longestStreak()).isEqualTo(3);       // 8/1~8/3 연속
        assertThat(response.totalAttendanceDays()).isEqualTo(4); // 출석 총 4일
        assertThat(response.totalStudiedSentences()).isEqualTo(15L);
        assertThat(response.totalStudyTimeSeconds()).isEqualTo(720.5);
        assertThat(response.studyDates()).isEqualTo(studyDates);

        // 레포트 날짜 그룹핑 검증
        assertThat(response.reportDates()).hasSize(2); // 8/1(레포트 2개), 8/3(레포트 1개)

        UserDashboardResponse.ReportDateEntry aug1 = response.reportDates().get(0);
        assertThat(aug1.date()).isEqualTo(LocalDate.of(2025, 8, 1));
        assertThat(aug1.reports()).hasSize(2);
        assertThat(aug1.reports().get(0).reportId()).isEqualTo(1L);
        assertThat(aug1.reports().get(0).sessionId()).isEqualTo(10L);
        assertThat(aug1.reports().get(0).videoTitle()).isEqualTo("Test Video");
        assertThat(aug1.reports().get(0).thumbnailUrl()).isEqualTo("https://thumb.jpg");
        assertThat(aug1.reports().get(1).reportId()).isEqualTo(2L);
        assertThat(aug1.reports().get(1).sessionId()).isEqualTo(11L);

        UserDashboardResponse.ReportDateEntry aug3 = response.reportDates().get(1);
        assertThat(aug3.date()).isEqualTo(LocalDate.of(2025, 8, 3));
        assertThat(aug3.reports()).hasSize(1);
        assertThat(aug3.reports().get(0).reportId()).isEqualTo(3L);

        then(attendanceLogRepository).should(times(1)).findVisitDatesByUserId(userId);
        then(evaluationRepository).should(times(1)).findDistinctStudyDatesByUserId(userId);
        then(sentenceRepository).should(times(1)).countStudiedSentencesByUserId(userId);
        then(sentenceRepository).should(times(1)).sumStudyTimeSecByUserId(userId);
        then(reportRepository).should(times(1)).findAllByUserIdWithVideoOrderByCreatedAtAsc(userId);
    }

    @Test
    @DisplayName("신규 사용자는 모든 통계가 0이고 빈 배열을 반환한다")
    void getDashboard_신규사용자_빈결과반환() {
        // given
        Long userId = 2L;

        given(attendanceLogRepository.findVisitDatesByUserId(userId)).willReturn(List.of());
        given(evaluationRepository.findDistinctStudyDatesByUserId(userId)).willReturn(List.of());
        given(sentenceRepository.countStudiedSentencesByUserId(userId)).willReturn(0L);
        given(sentenceRepository.sumStudyTimeSecByUserId(userId)).willReturn(0.0);
        given(reportRepository.findAllByUserIdWithVideoOrderByCreatedAtAsc(userId)).willReturn(List.of());

        // when
        UserDashboardResponse response = userService.getDashboard(userId);

        // then
        assertThat(response.longestStreak()).isZero();
        assertThat(response.totalAttendanceDays()).isZero();
        assertThat(response.totalStudiedSentences()).isZero();
        assertThat(response.totalStudyTimeSeconds()).isZero();
        assertThat(response.studyDates()).isEmpty();
        assertThat(response.reportDates()).isEmpty();
    }

    @Test
    @DisplayName("연속 출석일이 끊기면 최장 스트릭은 가장 긴 연속 구간을 반환한다")
    void getDashboard_스트릭계산_단절구간무시() {
        // given
        Long userId = 3L;

        // 8/1~8/2(2일), 8/5~8/8(4일) → longestStreak=4
        List<LocalDate> attendanceDates = List.of(
                LocalDate.of(2025, 8, 1),
                LocalDate.of(2025, 8, 2),
                LocalDate.of(2025, 8, 5),
                LocalDate.of(2025, 8, 6),
                LocalDate.of(2025, 8, 7),
                LocalDate.of(2025, 8, 8)
        );

        given(attendanceLogRepository.findVisitDatesByUserId(userId)).willReturn(attendanceDates);
        given(evaluationRepository.findDistinctStudyDatesByUserId(userId)).willReturn(List.of());
        given(sentenceRepository.countStudiedSentencesByUserId(userId)).willReturn(0L);
        given(sentenceRepository.sumStudyTimeSecByUserId(userId)).willReturn(0.0);
        given(reportRepository.findAllByUserIdWithVideoOrderByCreatedAtAsc(userId)).willReturn(List.of());

        // when
        UserDashboardResponse response = userService.getDashboard(userId);

        // then
        assertThat(response.longestStreak()).isEqualTo(4);
        assertThat(response.totalAttendanceDays()).isEqualTo(6);
    }

    // ===== 헬퍼 메서드 =====

    private User buildUser(Long userId) {
        User user = User.builder()
                .email("user@example.com")
                .nickname("브레맨")
                .provider("KAKAO")
                .providerId("kakao-123")
                .build();
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private Video buildVideo(String videoId, String title, String thumbnailUrl) {
        Video video = Video.builder()
                .videoId(videoId)
                .title(title)
                .embedUrl("https://www.youtube.com/embed/" + videoId)
                .thumbnailUrl(thumbnailUrl)
                .duration(120)
                .channelTitle("Test Channel")
                .build();
        return video;
    }

    private StudySession buildSession(Long sessionId, User user, Video video) {
        StudySession session = StudySession.builder()
                .video(video)
                .user(user)
                .startSec(0.0)
                .endSec(60.0)
                .build();
        ReflectionTestUtils.setField(session, "id", sessionId);
        return session;
    }

    private Report buildReport(Long reportId, StudySession session, LocalDateTime createdAt) {
        Report report = Report.builder()
                .studySession(session)
                .cycleNumber(1)
                .totalScore(BigDecimal.valueOf(85.0))
                .wordAccuracy(BigDecimal.valueOf(80.0))
                .prosodyAndStress(BigDecimal.valueOf(85.0))
                .wordRhythmScore(BigDecimal.valueOf(90.0))
                .boundaryToneScore(BigDecimal.valueOf(88.0))
                .dynamicStressScore(BigDecimal.valueOf(82.0))
                .speedSimilarity(BigDecimal.valueOf(87.0))
                .pauseSimilarity(BigDecimal.valueOf(83.0))
                .build();
        ReflectionTestUtils.setField(report, "id", reportId);
        ReflectionTestUtils.setField(report, "createdAt", createdAt);
        return report;
    }
}
