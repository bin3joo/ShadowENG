package com.bremenband.shadowengapi.domain.user.service;

import com.bremenband.shadowengapi.domain.study.repository.EvaluationRepository;
import com.bremenband.shadowengapi.domain.user.dto.res.UserInfoResponse;
import com.bremenband.shadowengapi.domain.user.entity.AttendanceLog;
import com.bremenband.shadowengapi.domain.user.entity.User;
import com.bremenband.shadowengapi.domain.user.repository.AttendanceLogRepository;
import com.bremenband.shadowengapi.domain.user.repository.UserRepository;
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
}
