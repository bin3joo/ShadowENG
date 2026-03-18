package com.bremenband.shadowengapi.domain.study.service;

import com.bremenband.shadowengapi.domain.study.client.PythonApiClient;
import com.bremenband.shadowengapi.domain.study.dto.python.PythonEvaluateAudioRequest;
import com.bremenband.shadowengapi.domain.study.dto.python.PythonEvaluateAudioResponse;
import com.bremenband.shadowengapi.domain.study.dto.redis.PendingEvaluation;
import com.bremenband.shadowengapi.domain.study.dto.res.EvaluationResponse;
import com.bremenband.shadowengapi.domain.study.entity.Evaluation;
import com.bremenband.shadowengapi.domain.study.entity.Sentence;
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
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EvaluationService {

    private final StudySessionRepository  studySessionRepository;
    private final SentenceRepository      sentenceRepository;
    private final EvaluationRepository    evaluationRepository;
    private final PythonApiClient         pythonApiClient;
    private final ObjectMapper            objectMapper;
    private final StudySessionWriter      studySessionWriter;
    private final S3Uploader              s3Uploader;
    private final PendingEvaluationStore  pendingEvaluationStore;

    public EvaluationResponse evaluate(Long sessionId, Long sentenceId, int step, MultipartFile audioFile, Long userId) {
        // 1. 세션 조회 및 소유권 검증
        StudySession session = studySessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));

        if (!session.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        // 2. 문장 조회 및 세션 소속 검증
        Sentence sentence = sentenceRepository.findById(sentenceId)
                .orElseThrow(() -> new CustomException(ErrorCode.SENTENCE_NOT_FOUND));

        if (!sentence.getStudySession().getId().equals(sessionId)) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        // 3. 음성 파일 S3 업로드
        String s3Key      = s3Uploader.upload(audioFile);
        String audioFormat = getFileExtension(audioFile.getOriginalFilename());

        // 4. 저장된 레퍼런스 JSON 역직렬화
        JsonNode features       = parseJson(sentence.getFeatures());
        JsonNode wordTimestamps = parseJson(sentence.getWordTimestamps());

        // 5. Python evaluate-audio 호출 (최대 35초 소요 — DB 커넥션 점유 방지를 위해 트랜잭션 밖에서 실행)
        PythonEvaluateAudioRequest request = new PythonEvaluateAudioRequest(
                s3Key, audioFormat, sentence.getContent(), features, wordTimestamps);
        PythonEvaluateAudioResponse pythonResponse = pythonApiClient.evaluateAudio(request);

        // 6. 평가 완료 후 S3 파일 삭제
        s3Uploader.delete(s3Key);

        if ("FAIL".equals(pythonResponse.status())) {
            throw new CustomException(ErrorCode.VOICE_RECOGNITION_FAILED);
        }

        // 7. 평가 결과를 Redis에 임시 저장 (step 1~4 공통)
        //    step 1~3: Redis에만 보관, DB 미저장 → 중도 이탈 시 TTL 만료로 자동 폐기
        //    step 4:   Redis 저장 후 사이클 전체를 DB에 일괄 커밋
        PendingEvaluation pending = buildPending(step, pythonResponse);
        pendingEvaluationStore.save(sessionId, sentenceId, pending);

        if (step == 4) {
            commitCycle(sessionId, sentenceId, session, sentence);
        }

        // 8. 응답 빌드 (step과 무관하게 Python 응답 그대로 반환)
        return buildResponse(sentence, pythonResponse);
    }

    /**
     * step 4 완료 시 Redis에 쌓인 사이클 전체(step 1~4)를 DB에 일괄 저장하고 Redis에서 삭제한다.
     * Redis에 일부 step이 없으면(TTL 만료 등) 존재하는 것만 저장한다.
     */
    private void commitCycle(Long sessionId, Long sentenceId, StudySession session, Sentence sentence) {
        List<PendingEvaluation> allPending = pendingEvaluationStore.findAll(sessionId, sentenceId);

        List<Evaluation> evaluations = allPending.stream()
                .map(p -> Evaluation.builder()
                        .studySession(session)
                        .sentence(sentence)
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
        pendingEvaluationStore.deleteAll(sessionId, sentenceId);
        studySessionWriter.completeSessionIfAllEvaluated(sessionId);
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

    private EvaluationResponse buildResponse(Sentence sentence, PythonEvaluateAudioResponse python) {
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
                sentence.getId(),
                sentence.getStartSec(),
                sentence.getEndSec(),
                sentence.getDurationSec(),
                python.userTranscription(),
                new EvaluationResponse.Details(wordFeedback, boundaryFeedback, dynamicFeedback),
                scores);
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "webm";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new CustomException(ErrorCode.DATA_CONVERSION_ERROR);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new CustomException(ErrorCode.DATA_CONVERSION_ERROR);
        }
    }

    private BigDecimal bd(double value) {
        return BigDecimal.valueOf(value);
    }
}
