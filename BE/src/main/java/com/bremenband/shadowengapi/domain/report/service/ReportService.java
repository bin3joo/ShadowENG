package com.bremenband.shadowengapi.domain.report.service;

import com.bremenband.shadowengapi.domain.report.dto.res.DailyReportResponse;
import com.bremenband.shadowengapi.domain.report.dto.res.ReportResponse;
import com.bremenband.shadowengapi.domain.report.entity.Report;
import com.bremenband.shadowengapi.domain.report.entity.WeekSentence;
import com.bremenband.shadowengapi.domain.report.repository.ReportRepository;
import com.bremenband.shadowengapi.domain.report.repository.WeekSentenceRepository;
import com.bremenband.shadowengapi.domain.study.entity.Evaluation;
import com.bremenband.shadowengapi.domain.study.entity.Sentence;
import com.bremenband.shadowengapi.domain.study.entity.StudySession;
import com.bremenband.shadowengapi.domain.study.repository.EvaluationRepository;
import com.bremenband.shadowengapi.domain.study.repository.StudySessionRepository;
import com.bremenband.shadowengapi.global.exception.CustomException;
import com.bremenband.shadowengapi.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private static final double DIFFICULT_THRESHOLD = 70.0;

    private final StudySessionRepository studySessionRepository;
    private final EvaluationRepository evaluationRepository;
    private final ReportRepository reportRepository;
    private final WeekSentenceRepository weekSentenceRepository;

    @Transactional
    public ReportResponse createReport(Long sessionId, Long userId) {
        // 1. 세션 조회
        StudySession session = studySessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));

        if (!session.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        // 2. 세션 평가 결과 조회
        List<Evaluation> evaluations = evaluationRepository.findByStudySession_Id(sessionId);
        if (evaluations.isEmpty()) {
            throw new CustomException(ErrorCode.NO_EVALUATIONS_FOR_REPORT);
        }

        // 3. 기존 레포트 있으면 삭제 후 재생성
        reportRepository.findByStudySession_Id(sessionId).ifPresent(existing -> {
            weekSentenceRepository.deleteByReport_Id(existing.getId());
            reportRepository.delete(existing);
        });

        // 4. 평균 점수 계산
        double avgTotal      = avg(evaluations, e -> e.getTotalScore().doubleValue());
        double avgAccuracy   = avg(evaluations, e -> e.getWordAccuracy().doubleValue());
        double avgProsody    = avg(evaluations, e -> e.getProsodyAndStress().doubleValue());
        double avgRhythm     = avg(evaluations, e -> e.getWordRhythmScore().doubleValue());
        double avgBoundary   = avg(evaluations, e -> e.getBoundaryToneScore().doubleValue());
        double avgDynamic    = avg(evaluations, e -> e.getDynamicStressScore().doubleValue());
        double avgSpeed      = avg(evaluations, e -> e.getSpeedSimilarity().doubleValue());
        double avgPause      = avg(evaluations, e -> e.getPauseSimilarity().doubleValue());

        // 5. 레포트 저장
        Report report = reportRepository.save(Report.builder()
                .studySession(session)
                .totalScore(bd(avgTotal))
                .wordAccuracy(bd(avgAccuracy))
                .prosodyAndStress(bd(avgProsody))
                .wordRhythmScore(bd(avgRhythm))
                .boundaryToneScore(bd(avgBoundary))
                .dynamicStressScore(bd(avgDynamic))
                .speedSimilarity(bd(avgSpeed))
                .pauseSimilarity(bd(avgPause))
                .build());

        // 6. 취약 문장 추출 (문장별 평균 totalScore < 70)
        Map<Long, List<Evaluation>> bySentence = evaluations.stream()
                .collect(Collectors.groupingBy(e -> e.getSentence().getId()));

        List<WeekSentence> weekSentences = bySentence.entrySet().stream()
                .filter(entry -> avgScore(entry.getValue()) < DIFFICULT_THRESHOLD)
                .map(entry -> {
                    Sentence sentence = entry.getValue().get(0).getSentence();
                    return weekSentenceRepository.save(WeekSentence.builder()
                            .report(report)
                            .sentence(sentence)
                            .build());
                })
                .toList();

        // 7. 응답 빌드
        List<ReportResponse.DifficultSentence> difficultSentences = weekSentences.stream()
                .map(ws -> new ReportResponse.DifficultSentence(
                        ws.getSentence().getId(),
                        ws.getSentence().getContent()))
                .toList();

        return new ReportResponse(
                sessionId,
                new ReportResponse.Scores(
                        round(avgTotal), round(avgAccuracy), round(avgProsody),
                        round(avgRhythm), round(avgBoundary), round(avgDynamic),
                        round(avgSpeed), round(avgPause)),
                difficultSentences);
    }

    @Transactional(readOnly = true)
    public DailyReportResponse getDailyReport(Long userId) {
        List<Evaluation> evaluations =
                evaluationRepository.findByStudySession_User_IdOrderByCreatedAtAsc(userId);

        Map<LocalDate, Long> countByDate = evaluations.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getCreatedAt().toLocalDate(),
                        TreeMap::new,
                        Collectors.counting()
                ));

        List<DailyReportResponse.StudyDayData> studyData = countByDate.entrySet().stream()
                .map(entry -> new DailyReportResponse.StudyDayData(
                        entry.getKey().toString(),
                        entry.getValue().intValue()))
                .toList();

        return new DailyReportResponse(studyData);
    }

    @Transactional(readOnly = true)
    public ReportResponse getReport(Long sessionId, Long userId) {
        Report report = reportRepository.findByStudySession_Id(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.REPORT_NOT_FOUND));

        if (!report.getStudySession().getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        List<WeekSentence> weekSentences = weekSentenceRepository.findByReport_Id(report.getId());

        List<ReportResponse.DifficultSentence> difficultSentences = weekSentences.stream()
                .map(ws -> new ReportResponse.DifficultSentence(
                        ws.getSentence().getId(),
                        ws.getSentence().getContent()))
                .toList();

        return new ReportResponse(
                sessionId,
                new ReportResponse.Scores(
                        report.getTotalScore().doubleValue(),
                        report.getWordAccuracy().doubleValue(),
                        report.getProsodyAndStress().doubleValue(),
                        report.getWordRhythmScore().doubleValue(),
                        report.getBoundaryToneScore().doubleValue(),
                        report.getDynamicStressScore().doubleValue(),
                        report.getSpeedSimilarity().doubleValue(),
                        report.getPauseSimilarity().doubleValue()),
                difficultSentences);
    }

    // ── 헬퍼 ────────────────────────────────────────────────────────────────────

    private double avg(List<Evaluation> evaluations, java.util.function.ToDoubleFunction<Evaluation> mapper) {
        return evaluations.stream().mapToDouble(mapper).average().orElse(0.0);
    }

    private double avgScore(List<Evaluation> evaluations) {
        return evaluations.stream()
                .mapToDouble(e -> e.getTotalScore().doubleValue())
                .average().orElse(100.0);
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private BigDecimal bd(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
