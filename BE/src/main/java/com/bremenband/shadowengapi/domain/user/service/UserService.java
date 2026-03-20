package com.bremenband.shadowengapi.domain.user.service;

import com.bremenband.shadowengapi.domain.study.repository.EvaluationRepository;
import com.bremenband.shadowengapi.domain.user.dto.res.UserInfoResponse;
import com.bremenband.shadowengapi.domain.user.entity.AttendanceLog;
import com.bremenband.shadowengapi.domain.user.entity.User;
import com.bremenband.shadowengapi.domain.user.repository.AttendanceLogRepository;
import com.bremenband.shadowengapi.domain.user.repository.UserRepository;
import com.bremenband.shadowengapi.global.exception.CustomException;
import com.bremenband.shadowengapi.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository        userRepository;
    private final EvaluationRepository  evaluationRepository;
    private final AttendanceLogRepository attendanceLogRepository;

    public UserInfoResponse getUserInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        List<LocalDate> attendanceDates = attendanceLogRepository.findVisitDatesByUserId(userId);
        List<LocalDate> studyDates      = evaluationRepository.findDistinctStudyDatesByUserId(userId);

        return new UserInfoResponse(
                user.getId(),
                user.getNickname(),
                user.getEmail(),
                user.getVisitedCount(),
                studyDates.size(),
                calculateLongestStreak(attendanceDates),
                studyDates,
                user.getCreatedAt()
        );
    }

    @Transactional
    public void incrementVisitedCount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        LocalDate today = LocalDate.now();
        boolean isNewDay = !today.equals(user.getLastVisitedDate());
        user.incrementVisitedCount();
        if (isNewDay) {
            attendanceLogRepository.save(AttendanceLog.of(userId, today));
        }
    }

    /**
     * 오름차순 정렬된 날짜 목록에서 최장 연속 학습일을 계산한다.
     */
    private int calculateLongestStreak(List<LocalDate> sortedDates) {
        if (sortedDates.isEmpty()) return 0;
        int longest = 1;
        int current = 1;
        for (int i = 1; i < sortedDates.size(); i++) {
            if (sortedDates.get(i).equals(sortedDates.get(i - 1).plusDays(1))) {
                current++;
                longest = Math.max(longest, current);
            } else {
                current = 1;
            }
        }
        return longest;
    }
}
