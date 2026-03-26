package com.bremenband.shadowengapi.domain.study.service;

import com.bremenband.shadowengapi.domain.study.client.PythonApiClient;
import com.bremenband.shadowengapi.domain.study.facade.StudyReportFacade;
import com.bremenband.shadowengapi.domain.study.dto.python.PythonEvaluateAudioRequest;
import com.bremenband.shadowengapi.domain.study.dto.python.PythonEvaluateAudioResponse;
import com.bremenband.shadowengapi.domain.study.dto.redis.PendingEvaluation;
import com.bremenband.shadowengapi.domain.study.dto.res.EvaluationResponse;
import com.bremenband.shadowengapi.domain.study.entity.Evaluation;
import com.bremenband.shadowengapi.domain.study.entity.Sentence;
import com.bremenband.shadowengapi.domain.study.entity.SessionStatus;
import com.bremenband.shadowengapi.domain.study.entity.StudySession;
import com.bremenband.shadowengapi.domain.study.repository.EvaluationRepository;
import com.bremenband.shadowengapi.domain.study.repository.SentenceRepository;
import com.bremenband.shadowengapi.domain.study.repository.StudySessionRepository;
import com.bremenband.shadowengapi.global.exception.CustomException;
import com.bremenband.shadowengapi.global.exception.ErrorCode;
import com.bremenband.shadowengapi.global.s3.S3Uploader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationService {

    private final StudySessionRepository  studySessionRepository;
    private final SentenceRepository      sentenceRepository;
    private final EvaluationRepository    evaluationRepository;
    private final PythonApiClient         pythonApiClient;
    private final ObjectMapper            objectMapper;
    private final StudyReportFacade       studyReportFacade;
    private final S3Uploader              s3Uploader;
    private final PendingEvaluationStore  pendingEvaluationStore;

    public EvaluationResponse evaluate(Long sessionId, Long sentenceId, int step, MultipartFile audioFile, Long userId) {
        log.info("[evaluate] start: sessionId={}, sentenceId={}, step={}, userId={}", sessionId, sentenceId, step, userId);

        // 1. 세션 조회 및 소유권 검증
        StudySession session = studySessionRepository.findById(sessionId)
                .orElseThrow(() -> {
                    log.warn("[evaluate] session not found: sessionId={}", sessionId);
                    return new CustomException(ErrorCode.SESSION_NOT_FOUND);
                });

        if (session.getStatus() == SessionStatus.DELETED) {
            log.warn("[evaluate] session is deleted: sessionId={}", sessionId);
            throw new CustomException(ErrorCode.SESSION_NOT_FOUND);
        }

        if (!session.getUser().getId().equals(userId)) {
            log.warn("[evaluate] forbidden: sessionId={}, userId={}", sessionId, userId);
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        // 2. 문장 조회 및 세션 소속 검증
        Sentence sentence = sentenceRepository.findById(sentenceId)
                .orElseThrow(() -> {
                    log.warn("[evaluate] sentence not found: sentenceId={}", sentenceId);
                    return new CustomException(ErrorCode.SENTENCE_NOT_FOUND);
                });

        if (!sentence.getStudySession().getId().equals(sessionId)) {
            log.warn("[evaluate] sentence not in session: sentenceId={}, sessionId={}", sentenceId, sessionId);
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        // 3. 복습 모드 검증 (첫 학습 이후에는 isReviewing=true 여야 진행 가능)
        if (session.getCycleCount() > 0 && !session.isReviewing()) {
            log.warn("[evaluate] review not started: sessionId={}, cycleCount={}", sessionId, session.getCycleCount());
            throw new CustomException(ErrorCode.REVIEW_NOT_STARTED);
        }

        // 4. 이전 step 완료 여부 검증 (step 2 이상이면 직전 step이 Redis에 존재해야 함)
        if (step > 2 && pendingEvaluationStore.findStep(sessionId, sentenceId, step - 1).isEmpty()) {
            log.warn("[evaluate] previous step not completed: sessionId={}, sentenceId={}, step={}", sessionId, sentenceId, step);
            throw new CustomException(ErrorCode.PREVIOUS_STEP_NOT_COMPLETED);
        }

        // 4. 음성 파일 유효성 검사 및 Base64 인코딩
        log.info("[evaluate] audio received: name={}, size={}bytes, contentType={}",
                audioFile.getOriginalFilename(), audioFile.getSize(), audioFile.getContentType());

        if (audioFile.getSize() < 5000) {
            log.warn("[evaluate] audio too short: size={}bytes", audioFile.getSize());
            throw new CustomException(ErrorCode.AUDIO_TOO_SHORT);
        }

        String audioBase64;
        try {
            audioBase64 = Base64.getEncoder().encodeToString(audioFile.getBytes());
        } catch (Exception e) {
            log.error("[evaluate] failed to encode audio to base64");
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        String audioFormat = getFileExtension(audioFile.getOriginalFilename());
        log.info("[evaluate] audio encoded to base64: format={}", audioFormat);

        // 4. S3에서 features fetch 후 역직렬화
        JsonNode features       = parseJson(s3Uploader.fetchJson(sentence.getFeaturesUrl()));
        JsonNode wordTimestamps = parseJson(sentence.getWordTimestamps());
        log.info("[evaluate] reference data parsed: sentenceId={}", sentenceId);

        // 5. Python evaluate-audio 호출 (최대 35초 소요 — DB 커넥션 점유 방지를 위해 트랜잭션 밖에서 실행)
        PythonEvaluateAudioRequest request = new PythonEvaluateAudioRequest(
                audioBase64, audioFormat, sentence.getContent(), features, wordTimestamps, null);
        log.info("[evaluate] calling python API: format={}, script=\"{}\"", audioFormat, sentence.getContent());

        PythonEvaluateAudioResponse pythonResponse = pythonApiClient.evaluateAudio(request);
        log.info("[Study.evaluate] reference sentence={}", sentence.getContent());
        log.info("[Study.evaluate] user transcription={}", pythonResponse.userTranscription());

        if ("FAIL".equals(pythonResponse.status())) {
            log.warn("[evaluate] voice recognition failed: sessionId={}, sentenceId={}, step={}", sessionId, sentenceId, step);
            throw new CustomException(ErrorCode.VOICE_RECOGNITION_FAILED);
        }

        log.info("[Study.evaluate] raw scores: wordAccuracy={}, prosodyAndStress={}, " +
                        "wordRhythmScore={}, boundaryToneScore={}, dynamicStressScore={}, " +
                        "speedSimilarity={}, pauseSimilarity={}",
                pythonResponse.scores().wordAccuracy(),
                pythonResponse.scores().prosodyAndStress(), pythonResponse.scores().wordRhythmScore(),
                pythonResponse.scores().boundaryToneScore(), pythonResponse.scores().dynamicStressScore(),
                pythonResponse.scores().speedSimilarity(), pythonResponse.scores().pauseSimilarity());

        // 6. 평가 결과를 Redis에 임시 저장 (step 1~4 공통)
        PendingEvaluation pending = buildPending(step, pythonResponse);
        pendingEvaluationStore.save(sessionId, sentenceId, pending);
        log.info("[evaluate] pending saved to redis: sessionId={}, sentenceId={}, step={}", sessionId, sentenceId, step);

        EvaluationResponse.Scores previousScores = null;
        if (step == 4) {
            // 복습 모드이면 commitCycle(DB 저장) 전에 직전 step4 점수를 조회
            if (session.isReviewing()) {
                previousScores = evaluationRepository
                        .findTopBySentence_IdAndStepOrderByCreatedAtDesc(sentenceId, 4)
                        .map(e -> new EvaluationResponse.Scores(
                                e.getTotalScore().doubleValue(),
                                e.getWordAccuracy().doubleValue(),
                                e.getProsodyAndStress().doubleValue(),
                                e.getWordRhythmScore().doubleValue(),
                                e.getBoundaryToneScore().doubleValue(),
                                e.getDynamicStressScore().doubleValue(),
                                e.getSpeedSimilarity().doubleValue(),
                                e.getPauseSimilarity().doubleValue()))
                        .orElse(null);
                log.info("[evaluate] previousScores loaded: sentenceId={}, found={}", sentenceId, previousScores != null);
            }
            log.info("[evaluate] step 4 detected, committing cycle: sessionId={}, sentenceId={}", sessionId, sentenceId);
            commitCycle(sessionId, sentenceId, session, sentence);
        }

        log.info("[evaluate] done: sessionId={}, sentenceId={}, step={}", sessionId, sentenceId, step);
        return buildResponse(step, sentence, pythonResponse, previousScores);
    }

    /**
     * step 4 완료 시 Redis에 쌓인 사이클 전체(step 1~4)를 DB에 일괄 저장하고 Redis에서 삭제한다.
     * Redis에 일부 step이 없으면(TTL 만료 등) 존재하는 것만 저장한다.
     */
    private void commitCycle(Long sessionId, Long sentenceId, StudySession session, Sentence sentence) {
        List<PendingEvaluation> allPending = pendingEvaluationStore.findAll(sessionId, sentenceId);
        log.info("[commitCycle] pending count from redis: sessionId={}, sentenceId={}, count={}", sessionId, sentenceId, allPending.size());

        List<Evaluation> evaluations = allPending.stream()
                .map(p -> Evaluation.builder()
                        .studySession(session)
                        .sentence(sentence)
                        .step(p.getStep())
                        .userTranscription(p.getUserTranscription())
                        .wordLevelFeedback(p.getWordLevelFeedback())
                        .boundaryToneFeedback(p.getBoundaryToneFeedback())
                        .dynamicStressFeedback(p.getDynamicStressFeedback())
                        .totalScore(bd(p.getTotalScore()))
                        .wordAccuracy(bd(p.getWordAccuracy()))
                        .prosodyAndStress(bd(p.getProsodyAndStress()))
                        .wordRhythmScore(bd(p.getWordRhythmScore()))
                        .boundaryToneScore(bd(p.getBoundaryToneScore()))
                        .dynamicStressScore(bd(p.getDynamicStressScore()))
                        .speedSimilarity(bd(p.getSpeedSimilarity()))
                        .pauseSimilarity(bd(p.getPauseSimilarity()))
                        .build())
                .toList();

        evaluationRepository.saveAll(evaluations);
        log.info("[commitCycle] evaluations saved to DB: count={}", evaluations.size());

        pendingEvaluationStore.deleteAll(sessionId, sentenceId);
        log.info("[commitCycle] redis keys deleted: sessionId={}, sentenceId={}", sessionId, sentenceId);

        // 복습(isReviewing=true) 시에는 사이클 완료 및 레포트 생성을 건너뜀
        if (!session.isReviewing()) {
            studyReportFacade.completeSessionAndAutoReport(sessionId, sentenceId);
            log.info("[commitCycle] session completion checked: sessionId={}", sessionId);
        } else {
            log.info("[commitCycle] skipped (review mode): sessionId={}", sessionId);
        }
    }

    private PendingEvaluation buildPending(int step, PythonEvaluateAudioResponse python) {
        PythonEvaluateAudioResponse.Details d = python.details();
        PythonEvaluateAudioResponse.Scores  s = python.scores();
        return new PendingEvaluation(
                step,
                python.userTranscription(),
                toJson(d.wordLevelFeedback()),
                toJson(d.boundaryToneFeedback()),
                toJson(d.dynamicStressFeedback()),
                s.totalScore(), s.wordAccuracy(), s.prosodyAndStress(),
                s.wordRhythmScore(), s.boundaryToneScore(), s.dynamicStressScore(),
                s.speedSimilarity(), s.pauseSimilarity()
        );
    }

    private EvaluationResponse buildResponse(int step, Sentence sentence, PythonEvaluateAudioResponse python,
                                              EvaluationResponse.Scores previousScores) {
        PythonEvaluateAudioResponse.Details d = python.details();
        PythonEvaluateAudioResponse.Scores  s = python.scores();

        List<EvaluationResponse.WordLevelFeedback> wordFeedback = d.wordLevelFeedback().stream()
                .map(w -> new EvaluationResponse.WordLevelFeedback(w.word(), w.status()))
                .toList();

        EvaluationResponse.BoundaryToneFeedback boundaryFeedback =
                new EvaluationResponse.BoundaryToneFeedback(d.boundaryToneFeedback().status());

        EvaluationResponse.DynamicStressFeedback dynamicFeedback =
                new EvaluationResponse.DynamicStressFeedback(d.dynamicStressFeedback().status());

        EvaluationResponse.Scores scores = new EvaluationResponse.Scores(
                s.totalScore(), s.wordAccuracy(), s.prosodyAndStress(),
                s.wordRhythmScore(), s.boundaryToneScore(), s.dynamicStressScore(),
                s.speedSimilarity(), s.pauseSimilarity());

        return new EvaluationResponse(
                step,
                sentence.getId(),
                sentence.getStartSec(),
                sentence.getEndSec(),
                sentence.getDurationSec(),
                python.userTranscription(),
                new EvaluationResponse.Details(wordFeedback, boundaryFeedback, dynamicFeedback),
                scores,
                previousScores);
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "webm";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.error("[evaluate] failed to parse json: {}", json);
            throw new CustomException(ErrorCode.DATA_CONVERSION_ERROR);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.error("[evaluate] failed to serialize object: {}", value);
            throw new CustomException(ErrorCode.DATA_CONVERSION_ERROR);
        }
    }

    private BigDecimal bd(double value) {
        return BigDecimal.valueOf(value);
    }
}
