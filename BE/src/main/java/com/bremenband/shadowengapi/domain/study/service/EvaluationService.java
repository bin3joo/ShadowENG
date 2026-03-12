package com.bremenband.shadowengapi.domain.study.service;

import com.bremenband.shadowengapi.domain.study.client.PythonApiClient;
import com.bremenband.shadowengapi.domain.study.dto.python.PythonEvaluateAudioRequest;
import com.bremenband.shadowengapi.domain.study.dto.python.PythonEvaluateAudioResponse;
import com.bremenband.shadowengapi.domain.study.dto.res.EvaluationResponse;
import com.bremenband.shadowengapi.domain.study.entity.Evaluation;
import com.bremenband.shadowengapi.domain.study.entity.Sentence;
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
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EvaluationService {

    private final StudySessionRepository studySessionRepository;
    private final SentenceRepository sentenceRepository;
    private final EvaluationRepository evaluationRepository;
    private final PythonApiClient pythonApiClient;
    private final ObjectMapper objectMapper;
    private final StudySessionWriter studySessionWriter;

    public EvaluationResponse evaluate(Long sessionId, Long sentenceId, int step, MultipartFile audioFile, Long userId) {
        // 1. 세션 조회 및 소유권 검증 (트랜잭션 불필요 — 단순 조회)
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

        // 3. 음성 파일 base64 인코딩
        String base64Audio = encodeToBase64(audioFile);
        String audioFormat = getFileExtension(audioFile.getOriginalFilename());

        // 4. 저장된 레퍼런스 JSON 역직렬화
        JsonNode features = parseJson(sentence.getFeatures());
        JsonNode wordTimestamps = parseJson(sentence.getWordTimestamps());

        // 5. Python evaluate-audio 호출 (최대 35초 소요 — DB 커넥션 점유 방지를 위해 트랜잭션 밖에서 실행)
        PythonEvaluateAudioRequest request = new PythonEvaluateAudioRequest(
                base64Audio, audioFormat, sentence.getContent(), features, wordTimestamps);
        PythonEvaluateAudioResponse pythonResponse = pythonApiClient.evaluateAudio(request);

        if ("FAIL".equals(pythonResponse.status())) {
            throw new CustomException(ErrorCode.VOICE_RECOGNITION_FAILED);
        }

        // 6. 평가 결과 저장 (SimpleJpaRepository.save()가 자체 @Transactional 보유)
        PythonEvaluateAudioResponse.Details d = pythonResponse.details();
        PythonEvaluateAudioResponse.Scores s = pythonResponse.scores();

        evaluationRepository.save(Evaluation.builder()
                .studySession(session)
                .sentence(sentence)
                .userTranscription(pythonResponse.userTranscription())
                .wordLevelFeedback(toJson(d.wordLevelFeedback()))
                .boundaryToneFeedback(toJson(d.boundaryToneFeedback()))
                .dynamicStressFeedback(toJson(d.dynamicStressFeedback()))
                .totalScore(bd(s.totalScore()))
                .wordAccuracy(bd(s.wordAccuracy()))
                .prosodyAndStress(bd(s.prosodyAndStress()))
                .wordRhythmScore(bd(s.wordRhythmScore()))
                .boundaryToneScore(bd(s.boundaryToneScore()))
                .dynamicStressScore(bd(s.dynamicStressScore()))
                .speedSimilarity(bd(s.speedSimilarity()))
                .pauseSimilarity(bd(s.pauseSimilarity()))
                .build());

        // 7. 모든 문장이 평가됐으면 세션 완료 처리
        studySessionWriter.completeSessionIfAllEvaluated(sessionId);

        // 8. 응답 빌드
        return buildResponse(sentence, pythonResponse);
    }

    private EvaluationResponse buildResponse(Sentence sentence, PythonEvaluateAudioResponse python) {
        PythonEvaluateAudioResponse.Details d = python.details();
        PythonEvaluateAudioResponse.Scores s = python.scores();

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

    private String encodeToBase64(MultipartFile file) {
        try {
            return Base64.getEncoder().encodeToString(file.getBytes());
        } catch (Exception e) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    //추후 파일 크기, 서버사이드 검증, 파일명 우회 가능성 등의 내용 수정 예정
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "wav";
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
