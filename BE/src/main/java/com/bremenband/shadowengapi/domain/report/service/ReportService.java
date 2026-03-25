package com.bremenband.shadowengapi.domain.report.service;

import com.bremenband.shadowengapi.domain.report.dto.res.DailyReportResponse;
import com.bremenband.shadowengapi.domain.report.dto.res.ReportResponse;
import com.bremenband.shadowengapi.domain.report.entity.Report;
import com.bremenband.shadowengapi.domain.report.entity.WeekSentence;
import com.bremenband.shadowengapi.domain.report.repository.ReportRepository;
import com.bremenband.shadowengapi.domain.report.repository.WeekSentenceRepository;
import com.bremenband.shadowengapi.domain.study.entity.Evaluation;
import com.bremenband.shadowengapi.domain.study.entity.Sentence;
import com.bremenband.shadowengapi.domain.study.entity.SessionStatus;
import com.bremenband.shadowengapi.domain.study.entity.StudySession;
import com.bremenband.shadowengapi.domain.study.repository.EvaluationRepository;
import com.bremenband.shadowengapi.domain.study.repository.SentenceRepository;
import com.bremenband.shadowengapi.domain.study.repository.StudySessionRepository;
import com.bremenband.shadowengapi.global.exception.CustomException;
import com.bremenband.shadowengapi.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private static final double DIFFICULT_THRESHOLD = 90.0;
    private static final int    DIFFICULT_SENTENCE_LIMIT = 3;

    private final StudySessionRepository studySessionRepository;
    private final EvaluationRepository   evaluationRepository;
    private final ReportRepository       reportRepository;
    private final WeekSentenceRepository weekSentenceRepository;
    private final SentenceRepository     sentenceRepository;
    private final ObjectMapper           objectMapper;

    @Transactional
    public ReportResponse createReport(Long sessionId, Long userId) {
        StudySession session = studySessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));

        if (session.getStatus() == SessionStatus.DELETED) {
            throw new CustomException(ErrorCode.SESSION_NOT_FOUND);
        }

        if (!session.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        return buildAndSaveReport(session, sessionId, session.getCycleCount());
    }

    /**
     * 인증 없이 서버 내부에서 레포트를 자동 생성할 때 사용 (사이클 완료 시 자동 호출).
     */
    @Transactional
    public void createReportInternal(Long sessionId, int cycleNumber) {
        StudySession session = studySessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));
        buildAndSaveReport(session, sessionId, cycleNumber);
    }

    private ReportResponse buildAndSaveReport(StudySession session, Long sessionId, int cycleNumber) {
        // 1. 세션 평가 결과 조회 (step 4 = 최종 숙달 확인 단계만 반영)
        List<Evaluation> evaluations = evaluationRepository.findByStudySession_IdAndStep(sessionId, 4);
        if (evaluations.isEmpty()) {
            throw new CustomException(ErrorCode.NO_EVALUATIONS_FOR_REPORT);
        }

        // 2. 평균 점수 계산
        double avgTotal    = avg(evaluations, e -> e.getTotalScore().doubleValue());
        double avgAccuracy = avg(evaluations, e -> e.getWordAccuracy().doubleValue());
        double avgProsody  = avg(evaluations, e -> e.getProsodyAndStress().doubleValue());
        double avgRhythm   = avg(evaluations, e -> e.getWordRhythmScore().doubleValue());
        double avgBoundary = avg(evaluations, e -> e.getBoundaryToneScore().doubleValue());
        double avgDynamic  = avg(evaluations, e -> e.getDynamicStressScore().doubleValue());
        double avgSpeed    = avg(evaluations, e -> e.getSpeedSimilarity().doubleValue());
        double avgPause    = avg(evaluations, e -> e.getPauseSimilarity().doubleValue());

        // 3. 레포트 저장
        Report report = reportRepository.save(Report.builder()
                .studySession(session)
                .cycleNumber(cycleNumber)
                .totalScore(bd(avgTotal))
                .wordAccuracy(bd(avgAccuracy))
                .prosodyAndStress(bd(avgProsody))
                .wordRhythmScore(bd(avgRhythm))
                .boundaryToneScore(bd(avgBoundary))
                .dynamicStressScore(bd(avgDynamic))
                .speedSimilarity(bd(avgSpeed))
                .pauseSimilarity(bd(avgPause))
                .build());

        // 4. 취약 문장 추출 — avgScore 낮은 순 정렬
        Map<Long, List<Evaluation>> bySentence = evaluations.stream()
                .collect(Collectors.groupingBy(e -> e.getSentence().getId()));

        List<Map.Entry<Long, List<Evaluation>>> difficultEntries = bySentence.entrySet().stream()
                .filter(entry -> avgScore(entry.getValue()) < DIFFICULT_THRESHOLD)
                .sorted(Comparator.comparingDouble(entry -> avgScore(entry.getValue())))
                .toList();

        difficultEntries.forEach(entry -> {
            Sentence sentence = entry.getValue().get(0).getSentence();
            weekSentenceRepository.save(WeekSentence.builder()
                    .report(report).sentence(sentence).build());
        });

        // 5. 응답 (상위 3개)
        List<ReportResponse.DifficultSentence> difficultSentences = difficultEntries.stream()
                .limit(DIFFICULT_SENTENCE_LIMIT)
                .map(entry -> buildDifficultSentence(
                        entry.getValue().get(0).getSentence(),
                        avgScore(entry.getValue()),
                        getLatestEvaluation(entry.getValue())))
                .toList();

        int totalSentenceCount = (int) sentenceRepository.countByStudySession_Id(sessionId);

        return new ReportResponse(
                report.getId(),
                sessionId,
                report.getCreatedAt(),
                report.getCycleNumber(),
                totalSentenceCount,
                new ReportResponse.Scores(
                        round(avgTotal), round(avgAccuracy), round(avgProsody),
                        round(avgRhythm), round(avgBoundary), round(avgDynamic),
                        round(avgSpeed), round(avgPause)),
                difficultSentences);
    }

    @Transactional(readOnly = true)
    public List<ReportResponse> getReports(Long sessionId, Long userId) {
        StudySession session = studySessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));

        if (session.getStatus() == SessionStatus.DELETED) {
            throw new CustomException(ErrorCode.SESSION_NOT_FOUND);
        }

        if (!session.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        List<Report> reports = reportRepository.findByStudySession_IdOrderByCreatedAtDesc(sessionId);
        List<Evaluation> allEvaluations = evaluationRepository.findByStudySession_IdAndStep(sessionId, 4);
        Map<Long, List<Evaluation>> evalsBySentence = allEvaluations.stream()
                .collect(Collectors.groupingBy(e -> e.getSentence().getId()));

        int totalSentenceCount = (int) sentenceRepository.countByStudySession_Id(sessionId);

        return reports.stream()
                .map(report -> buildReportResponse(report, sessionId, totalSentenceCount, evalsBySentence))
                .toList();
    }

    @Transactional(readOnly = true)
    public ReportResponse getReport(Long sessionId, Long reportId, Long userId) {
        Report report = reportRepository.findByIdAndStudySession_Id(reportId, sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.REPORT_NOT_FOUND));

        if (!report.getStudySession().getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        List<WeekSentence> weekSentences = weekSentenceRepository.findByReport_Id(report.getId());

        // 평가 데이터 조회 (step 4만, avgScore 계산 및 상태값 추출용)
        List<Evaluation> allEvaluations = evaluationRepository.findByStudySession_IdAndStep(sessionId, 4);
        Map<Long, List<Evaluation>> evalsBySentence = allEvaluations.stream()
                .collect(Collectors.groupingBy(e -> e.getSentence().getId()));

        List<ReportResponse.DifficultSentence> difficultSentences = weekSentences.stream()
                .map(ws -> {
                    List<Evaluation> evals = evalsBySentence.getOrDefault(ws.getSentence().getId(), List.of());
                    return buildDifficultSentence(
                            ws.getSentence(),
                            avgScore(evals),
                            getLatestEvaluation(evals));
                })
                .sorted(Comparator.comparingDouble(ReportResponse.DifficultSentence::averageScore))
                .limit(DIFFICULT_SENTENCE_LIMIT)
                .toList();

        int totalSentenceCount = (int) sentenceRepository.countByStudySession_Id(sessionId);

        return new ReportResponse(
                report.getId(),
                sessionId,
                report.getCreatedAt(),
                report.getCycleNumber(),
                totalSentenceCount,
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

    @Transactional(readOnly = true)
    public List<ReportResponse> getUserReports(Long userId) {
        List<Report> reports = reportRepository.findByStudySession_User_IdOrderByCreatedAtDesc(userId);

        List<Long> sessionIds = reports.stream()
                .map(r -> r.getStudySession().getId())
                .distinct().toList();
        Map<Long, Long> sentenceCountMap = sentenceRepository.countMapBySessionIds(sessionIds);

        return reports.stream()
                .map(report -> {
                    Long sid = report.getStudySession().getId();
                    List<Evaluation> allEvaluations = evaluationRepository.findByStudySession_IdAndStep(sid, 4);
                    Map<Long, List<Evaluation>> evalsBySentence = allEvaluations.stream()
                            .collect(Collectors.groupingBy(e -> e.getSentence().getId()));
                    int count = sentenceCountMap.getOrDefault(sid, 0L).intValue();
                    return buildReportResponse(report, sid, count, evalsBySentence);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public DailyReportResponse getDailyReport(Long userId) {
        List<Evaluation> evaluations =
                evaluationRepository.findByStudySession_User_IdAndStepOrderByCreatedAtAsc(userId, 4);

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

    // ── 헬퍼 ────────────────────────────────────────────────────────────────────

    private ReportResponse buildReportResponse(Report report, Long sessionId, int totalSentenceCount,
                                               Map<Long, List<Evaluation>> evalsBySentence) {
        List<WeekSentence> weekSentences = weekSentenceRepository.findByReport_Id(report.getId());

        List<ReportResponse.DifficultSentence> difficultSentences = weekSentences.stream()
                .map(ws -> {
                    List<Evaluation> evals = evalsBySentence.getOrDefault(ws.getSentence().getId(), List.of());
                    return buildDifficultSentence(
                            ws.getSentence(),
                            avgScore(evals),
                            getLatestEvaluation(evals));
                })
                .sorted(Comparator.comparingDouble(ReportResponse.DifficultSentence::averageScore))
                .limit(DIFFICULT_SENTENCE_LIMIT)
                .toList();

        return new ReportResponse(
                report.getId(),
                sessionId,
                report.getCreatedAt(),
                report.getCycleNumber(),
                totalSentenceCount,
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

    private ReportResponse.DifficultSentence buildDifficultSentence(
            Sentence sentence, double avgScore, Evaluation latest) {
        String boundaryStatus = null;
        String dynamicStatus  = null;
        List<ReportResponse.DifficultSentence.WordFeedback> wordFeedback = List.of();

        if (latest != null) {
            boundaryStatus = parseStatus(latest.getBoundaryToneFeedback());
            dynamicStatus  = parseStatus(latest.getDynamicStressFeedback());
            wordFeedback   = parseWordFeedback(latest.getWordLevelFeedback());
        }

        return new ReportResponse.DifficultSentence(
                sentence.getId(),
                sentence.getContent(),
                round(avgScore),
                boundaryStatus,
                dynamicStatus,
                wordFeedback);
    }

    private Evaluation getLatestEvaluation(List<Evaluation> evaluations) {
        return evaluations.stream()
                .max(Comparator.comparing(Evaluation::getCreatedAt))
                .orElse(null);
    }

    private String parseStatus(String json) {
        if (json == null) return null;
        try {
            return objectMapper.readTree(json).path("status").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    private List<ReportResponse.DifficultSentence.WordFeedback> parseWordFeedback(String json) {
        if (json == null) return List.of();
        try {
            JsonNode arr = objectMapper.readTree(json);
            List<ReportResponse.DifficultSentence.WordFeedback> list = new ArrayList<>();
            for (JsonNode node : arr) {
                list.add(new ReportResponse.DifficultSentence.WordFeedback(
                        node.path("word").asText(),
                        node.path("status").asText()));
            }
            return list;
        } catch (Exception e) {
            return List.of();
        }
    }

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
