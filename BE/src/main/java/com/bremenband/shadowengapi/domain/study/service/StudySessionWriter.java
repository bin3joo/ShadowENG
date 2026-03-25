package com.bremenband.shadowengapi.domain.study.service;

import com.bremenband.shadowengapi.domain.study.dto.res.StudySessionCreateResponse;
import com.bremenband.shadowengapi.domain.study.dto.transcription.TranscribedSentence;
import com.bremenband.shadowengapi.domain.study.entity.Sentence;
import com.bremenband.shadowengapi.domain.study.entity.StudySession;
import com.bremenband.shadowengapi.domain.study.repository.EvaluationRepository;
import com.bremenband.shadowengapi.domain.study.repository.SentenceRepository;
import com.bremenband.shadowengapi.domain.study.repository.StudySessionRepository;
import com.bremenband.shadowengapi.global.exception.CustomException;
import com.bremenband.shadowengapi.global.exception.ErrorCode;
import com.bremenband.shadowengapi.domain.user.entity.User;
import com.bremenband.shadowengapi.domain.youtube.entity.Video;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.IntStream;

/**
 * 세션 + 문장 저장의 원자성을 보장하기 위한 별도 빈.
 * StudySessionService에서 self-invocation 시 @Transactional이 적용되지 않는 문제를 회피.
 */
@Component
@RequiredArgsConstructor
public class StudySessionWriter {

    private final StudySessionRepository studySessionRepository;
    private final SentenceRepository sentenceRepository;
    private final EvaluationRepository evaluationRepository;
    private final ObjectMapper objectMapper;

    /**
     * step 4 완료 시 호출.
     * 모든 문장이 학습 완료되면 사이클을 증가시키고 새 cycleCount를 반환한다.
     * 미완료 시 0을 반환한다.
     */
    @Transactional
    public int completeSessionIfAllEvaluated(Long sessionId, Long sentenceId) {
        // 1. step 4 완료된 문장의 studyCount 증가
        Sentence sentence = sentenceRepository.findById(sentenceId)
                .orElseThrow(() -> new CustomException(ErrorCode.SENTENCE_NOT_FOUND));
        sentence.incrementStudyCount();

        // 2. 세션 및 전체 문장 조회
        StudySession session = studySessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));
        List<Sentence> allSentences = sentenceRepository.findByStudySession_IdOrderByIdAsc(sessionId);

        if (allSentences.isEmpty()) return 0;

        // 3. 모든 문장의 studyCount > cycleCount 이면 사이클 완료
        boolean allAhead = allSentences.stream()
                .allMatch(s -> s.getStudyCount() > session.getCycleCount());
        if (allAhead) {
            session.completeCycle();  // cycleCount++, isReviewing=false
            session.complete();       // status=COMPLETED (최초 1회 이후 멱등)
            return session.getCycleCount();
        }
        return 0;
    }

    @Transactional
    public StudySessionCreateResponse saveSessionAndSentences(
            User user, Video video, double startSec, double endSec,
            List<TranscribedSentence> transcriptions) {

        StudySession session = studySessionRepository.save(StudySession.builder()
                .video(video)
                .user(user)
                .startSec(startSec)
                .endSec(endSec)
                .build());

        List<Sentence> sentencesToSave = transcriptions.stream()
                .map(t -> Sentence.builder()
                        .studySession(session)
                        .content(t.content())
                        .startSec(t.startSec())
                        .endSec(t.endSec())
                        .durationSec(t.durationSec())
                        .wordTimestamps(t.wordTimestamps())
                        .featuresUrl(t.featuresUrl())
                        .sentenceKo(t.sentenceKo())
                        .difficulty(t.difficulty())
                        .difficultyScore(t.difficultyScore())
                        .keyExpressionsJson(toJson(t.keyExpressions()))
                        .vocabularyJson(toJson(t.vocabulary()))
                        .build())
                .toList();
        List<Sentence> sentences = sentenceRepository.saveAll(sentencesToSave);

        return new StudySessionCreateResponse(
                session.getId(),
                null,
                new StudySessionCreateResponse.VideoData(
                        video.getVideoId(),
                        video.getEmbedUrl(),
                        "https://www.youtube.com/watch?v=" + video.getVideoId(),
                        video.getTitle(),
                        video.getThumbnailUrl(),
                        video.getDuration(),
                        video.getChannelTitle()
                ),
                IntStream.range(0, sentences.size())
                        .mapToObj(i -> {
                            Sentence s = sentences.get(i);
                            TranscribedSentence t = transcriptions.get(i);
                            return new StudySessionCreateResponse.SentenceData(
                                    s.getId(),
                                    i + 1,
                                    s.getContent(),
                                    s.getStartSec(),
                                    s.getEndSec(),
                                    s.getDurationSec(),
                                    0,
                                    t.sentenceKo(),
                                    t.keyExpressions(),
                                    t.difficulty(),
                                    t.difficultyScore(),
                                    t.vocabulary(),
                                    t.wordTimestampList()
                            );
                        })
                        .toList(),
                false
        );
    }

    /**
     * 재학습 시 호출. 원본 세션의 모든 정보를 복사하여 새 자식 세션을 생성한다.
     */
    @Transactional
    public record ReLearnResult(StudySession session, List<Sentence> sentences) {}

    @Transactional
    public ReLearnResult saveReLearnSession(StudySession original, User user, List<Sentence> originalSentences) {
        StudySession newSession = studySessionRepository.save(StudySession.builder()
                .video(original.getVideo())
                .user(user)
                .startSec(original.getStartSec())
                .endSec(original.getEndSec())
                .parentSession(original)
                .build());

        List<Sentence> copiedSentences = originalSentences.stream()
                .map(s -> Sentence.builder()
                        .studySession(newSession)
                        .content(s.getContent())
                        .startSec(s.getStartSec())
                        .endSec(s.getEndSec())
                        .durationSec(s.getDurationSec())
                        .wordTimestamps(s.getWordTimestamps())
                        .featuresUrl(s.getFeaturesUrl())
                        .sentenceKo(s.getSentenceKo())
                        .difficulty(s.getDifficulty())
                        .difficultyScore(s.getDifficultyScore())
                        .keyExpressionsJson(s.getKeyExpressionsJson())
                        .vocabularyJson(s.getVocabularyJson())
                        .build())
                .toList();

        return new ReLearnResult(newSession, sentenceRepository.saveAll(copiedSentences));
    }

    private String toJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new CustomException(ErrorCode.DATA_CONVERSION_ERROR);
        }
    }
}
