package com.bremenband.shadowengapi.domain.user.service;

import com.bremenband.shadowengapi.domain.report.entity.Report;
import com.bremenband.shadowengapi.domain.report.repository.ReportRepository;
import com.bremenband.shadowengapi.domain.study.repository.EvaluationRepository;
import com.bremenband.shadowengapi.domain.study.repository.SentenceRepository;
import com.bremenband.shadowengapi.domain.user.dto.res.UserDashboardResponse;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository          userRepository;
    private final EvaluationRepository   evaluationRepository;
    private final AttendanceLogRepository attendanceLogRepository;
    private final SentenceRepository     sentenceRepository;
    private final ReportRepository       reportRepository;

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

    public UserDashboardResponse getDashboard(Long userId) {
        // 출석 관련
        List<LocalDate> attendanceDates = attendanceLogRepository.findVisitDatesByUserId(userId);
        int totalAttendanceDays = attendanceDates.size();
        int longestStreak = calculateLongestStreak(attendanceDates);

        // 학습 날짜
        List<LocalDate> studyDates = evaluationRepository.findDistinctStudyDatesByUserId(userId);

        // 학습 통계
        long totalStudiedSentences = sentenceRepository.countStudiedSentencesByUserId(userId);
        double totalStudyTimeSeconds = sentenceRepository.sumStudyTimeSecByUserId(userId);

        // 레포트 날짜별 그룹핑
        List<Report> reports = reportRepository.findAllByUserIdWithVideoOrderByCreatedAtAsc(userId);
        List<UserDashboardResponse.ReportDateEntry> reportDates = reports.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getCreatedAt().toLocalDate(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ))
                .entrySet().stream()
                .map(e -> new UserDashboardResponse.ReportDateEntry(
                        e.getKey(),
                        e.getValue().stream()
                                .map(r -> new UserDashboardResponse.ReportInfo(
                                        r.getId(),
                                        r.getStudySession().getId(),
                                        r.getStudySession().getVideo().getThumbnailUrl(),
                                        r.getStudySession().getVideo().getTitle()
                                ))
                                .toList()
                ))
                .toList();

        return new UserDashboardResponse(
                longestStreak,
                totalAttendanceDays,
                totalStudiedSentences,
                totalStudyTimeSeconds,
                studyDates,
                reportDates
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
