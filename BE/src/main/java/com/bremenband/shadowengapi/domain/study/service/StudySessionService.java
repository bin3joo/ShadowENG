package com.bremenband.shadowengapi.domain.study.service;

import com.bremenband.shadowengapi.domain.study.dto.req.StudySessionCreateRequest;
import com.bremenband.shadowengapi.domain.study.dto.res.ActiveSessionResponse;
import com.bremenband.shadowengapi.domain.study.dto.res.ActiveSessionsResponse;
import com.bremenband.shadowengapi.domain.study.dto.res.LatestActiveSessionResponse;
import com.bremenband.shadowengapi.domain.study.dto.res.RecentStudySessionResponse;
import com.bremenband.shadowengapi.domain.study.dto.res.SentenceLearningResponse;
import com.bremenband.shadowengapi.domain.study.dto.res.StudySessionCreateResponse;
import com.bremenband.shadowengapi.domain.study.dto.redis.PendingEvaluation;
import com.bremenband.shadowengapi.domain.study.dto.python.PythonGenerateReferenceResponse;
import com.bremenband.shadowengapi.domain.study.util.WordMasker;
import com.bremenband.shadowengapi.domain.study.dto.transcription.TranscribedSentence;
import com.bremenband.shadowengapi.domain.study.entity.SessionStatus;
import com.bremenband.shadowengapi.domain.study.entity.StudySession;
import com.bremenband.shadowengapi.domain.study.entity.Sentence;
import com.bremenband.shadowengapi.domain.study.repository.EvaluationRepository;
import com.bremenband.shadowengapi.domain.study.repository.SentenceRepository;
import com.bremenband.shadowengapi.domain.study.repository.StudySessionRepository;
import com.bremenband.shadowengapi.domain.user.entity.User;
import com.bremenband.shadowengapi.domain.user.repository.UserRepository;
import com.bremenband.shadowengapi.domain.youtube.dto.res.ThumbnailInfo;
import com.bremenband.shadowengapi.domain.youtube.dto.res.VideoInfoResponse;
import com.bremenband.shadowengapi.domain.youtube.entity.Video;
import com.bremenband.shadowengapi.domain.youtube.repository.VideoRepository;
import com.bremenband.shadowengapi.domain.youtube.service.YoutubeService;
import com.bremenband.shadowengapi.global.exception.CustomException;
import com.bremenband.shadowengapi.global.exception.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudySessionService {

    private final StudySessionRepository studySessionRepository;
    private final VideoRepository videoRepository;
    private final SentenceRepository sentenceRepository;
    private final EvaluationRepository evaluationRepository;
    private final UserRepository userRepository;
    private final YoutubeService youtubeService;
    private final TranscriptionService transcriptionService;
    private final StudySessionWriter     studySessionWriter;
    private final ObjectMapper           objectMapper;
    private final PendingEvaluationStore pendingEvaluationStore;

    public ActiveSessionsResponse getAllSessions(Long userId) {
        List<StudySession> sessions = studySessionRepository
                .findByUser_IdAndStatusNotOrderByCreatedAtDesc(userId, SessionStatus.DELETED);

        if (sessions.isEmpty()) {
            return new ActiveSessionsResponse(List.of(), List.of());
        }

        List<Long> sessionIds = sessions.stream().map(StudySession::getId).toList();

        // 배치 집계: 세션별 전체 문장 수, 학습 완료 문장 수 (N+1 방지)
        Map<Long, Long> totalMap     = sentenceRepository.countMapBySessionIds(sessionIds);
        Map<Long, Long> completedMap = evaluationRepository.completedSentencesMapBySessionIds(sessionIds);

        Map<SessionStatus, List<ActiveSessionResponse>> grouped = sessions.stream()
                .collect(Collectors.groupingBy(
                        StudySession::getStatus,
                        Collectors.mapping(session -> {
                            String videoId = session.getVideo().getVideoId();
                            ThumbnailInfo thumbnail = new ThumbnailInfo(
                                    "https://i.ytimg.com/vi/" + videoId + "/sddefault.jpg", 640, 480);
                            long total     = totalMap.getOrDefault(session.getId(), 0L);
                            long completed = completedMap.getOrDefault(session.getId(), 0L);
                            return new ActiveSessionResponse(session.getId(), session.getVideo().getTitle(), thumbnail, total, completed);
                        }, Collectors.toList())
                ));

        return new ActiveSessionsResponse(
                grouped.getOrDefault(SessionStatus.ACTIVE, List.of()),
                grouped.getOrDefault(SessionStatus.COMPLETED, List.of())
        );
    }

    public StudySessionCreateResponse getStudySession(Long sessionId, Long userId) {
        StudySession session = studySessionRepository.findByIdWithVideo(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));

        if (session.getStatus() == SessionStatus.DELETED) {
            throw new CustomException(ErrorCode.SESSION_NOT_FOUND);
        }

        if (!session.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        Video video = session.getVideo();
        List<Sentence> sentences = sentenceRepository.findByStudySession_IdOrderByIdAsc(sessionId);

        List<StudySessionCreateResponse.SentenceData> sentencesData = IntStream.range(0, sentences.size())
                .mapToObj(i -> {
                    Sentence s = sentences.get(i);
                    SentenceMetadata meta = parseSentenceMetadata(s);
                    return new StudySessionCreateResponse.SentenceData(
                            s.getId(),
                            i + 1,
                            s.getContent(),
                            s.getStartSec(),
                            s.getEndSec(),
                            s.getDurationSec(),
                            s.getStudyCount(),
                            meta.sentenceKo(),
                            meta.keyExpressions(),
                            meta.difficulty(),
                            meta.difficultyScore(),
                            meta.vocabulary(),
                            meta.wordTimestamps()
                    );
                })
                .toList();

        Long parentSessionId = session.getParentSession() != null
                ? session.getParentSession().getId() : null;

        return new StudySessionCreateResponse(
                session.getId(),
                parentSessionId,
                new StudySessionCreateResponse.VideoData(
                        video.getVideoId(),
                        video.getEmbedUrl(),
                        "https://www.youtube.com/watch?v=" + video.getVideoId(),
                        video.getTitle(),
                        video.getThumbnailUrl(),
                        video.getDuration(),
                        video.getChannelTitle()
                ),
                sentencesData,
                session.isReviewing()
        );
    }

    public RecentStudySessionResponse getRecentSession(Long userId) {
        Optional<StudySession> sessionOpt = studySessionRepository
                .findTopByUser_IdAndStatusOrderByCreatedAtDesc(userId, SessionStatus.ACTIVE);

        if (sessionOpt.isEmpty()) {
            return new RecentStudySessionResponse(null);
        }

        StudySession session = sessionOpt.get();
        String videoId = session.getVideo().getVideoId();
        String thumbnailUrl = "https://i.ytimg.com/vi/" + videoId + "/sddefault.jpg";

        long totalSentences     = sentenceRepository.countByStudySession_Id(session.getId());
        long completedSentences = evaluationRepository.countDistinctEvaluatedSentencesBySessionId(session.getId());

        LatestActiveSessionResponse latestSession = new LatestActiveSessionResponse(
                session.getId(),
                thumbnailUrl,
                session.getVideo().getTitle(),
                totalSentences,
                completedSentences
        );

        return new RecentStudySessionResponse(latestSession);
    }

    @Transactional
    public StudySessionCreateResponse createStudySession(Long userId, StudySessionCreateRequest request) {
        // 1. URL에서 videoId 추출 (watch/youtu.be/embed 형식 모두 지원, DB/API 호출 없음)
        String videoId = youtubeService.extractVideoId(request.embedUrl());

        // 2. Video 조회 or YouTube API에서 가져와 저장 (각각 독립 트랜잭션, 최대 10초)
        Video video = videoRepository.findById(videoId)
                .orElseGet(() -> {
                    VideoInfoResponse info = youtubeService.getVideo(request.embedUrl());
                    return videoRepository.save(Video.builder()
                            .videoId(info.videoId())
                            .title(info.title())
                            .embedUrl(info.embedUrl())
                            .thumbnailUrl(info.thumbnailUrl())
                            .duration((int) info.duration())
                            .channelTitle(info.channelTitle())
                            .build());
                });

        // 3. User 조회 (독립 트랜잭션)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 4. 전사(STT) 수행 (Python API 호출, 최대 35초 — DB 커넥션 점유 없음)
        List<TranscribedSentence> transcriptions =
                transcriptionService.transcribe(videoId, request.startSec(), request.endSec());

        // 5. 세션 + 문장 저장 (단일 트랜잭션으로 원자성 보장)
        return studySessionWriter.saveSessionAndSentences(
                user, video, request.startSec(), request.endSec(), transcriptions);
    }

    public SentenceLearningResponse getSentenceLearning(Long sessionId, Long sentenceId, int step, Long userId) {
        // 1. 세션 조회 및 소유권 검증
        StudySession session = studySessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));

        if (session.getStatus() == SessionStatus.DELETED) {
            throw new CustomException(ErrorCode.SESSION_NOT_FOUND);
        }

        if (!session.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        // 2. 문장 조회 및 세션 소속 검증
        Sentence sentence = sentenceRepository.findById(sentenceId)
                .orElseThrow(() -> new CustomException(ErrorCode.SENTENCE_NOT_FOUND));

        if (!sentence.getStudySession().getId().equals(sessionId)) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        // 3. step별 응답 결정
        //    step 1: 청취/쉐도잉 단계      — 텍스트 없음 (sentence=null, hiddenSentence=null)
        //    step 2: 원문 읽기 단계        — 원문만 제공
        //    step 3: 원문+빈칸 채우기 단계  — 원문과 마스킹 문장 모두 제공 (step2 평가 결과 기반)
        //    step 4: 청취/쉐도잉 반복 단계  — step 1과 동일, 텍스트 없음
        String fullSentence   = (step == 2 || step == 3) ? sentence.getContent() : null;
        String hiddenSentence = step == 3 ? buildHiddenSentence(sessionId, sentenceId, sentence.getContent()) : null;

        boolean isReviewing = session.isReviewing();
        boolean needsReview = sentence.getStudyCount() <= session.getCycleCount();
        SentenceMetadata meta = parseSentenceMetadata(sentence);

        return new SentenceLearningResponse(
                step,
                sessionId,
                sentenceId,
                fullSentence,
                hiddenSentence,
                sentence.getStartSec(),
                sentence.getEndSec(),
                sentence.getDurationSec(),
                sentence.getStudyCount(),
                isReviewing,
                needsReview,
                meta.sentenceKo(),
                meta.keyExpressions(),
                meta.difficulty(),
                meta.difficultyScore(),
                meta.vocabulary(),
                meta.wordTimestamps()
        );
    }

    @Transactional
    public StudySessionCreateResponse reLearnSession(Long sessionId, Long userId) {
        StudySession original = studySessionRepository.findByIdWithVideo(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));

        if (original.getStatus() == SessionStatus.DELETED) {
            throw new CustomException(ErrorCode.SESSION_NOT_FOUND);
        }

        if (original.getStatus() != SessionStatus.COMPLETED) {
            throw new CustomException(ErrorCode.SESSION_NOT_COMPLETED);
        }

        if (!original.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        User user = original.getUser();
        List<Sentence> originalSentences = sentenceRepository.findByStudySession_IdOrderByIdAsc(sessionId);

        StudySessionWriter.ReLearnResult result = studySessionWriter.saveReLearnSession(original, user, originalSentences);
        StudySession newSession = result.session();
        Video video = original.getVideo();

        List<StudySessionCreateResponse.SentenceData> sentencesData = IntStream.range(0, result.sentences().size())
                .mapToObj(i -> {
                    Sentence s = result.sentences().get(i);
                    SentenceMetadata meta = parseSentenceMetadata(s);
                    return new StudySessionCreateResponse.SentenceData(
                            s.getId(), i + 1, s.getContent(),
                            s.getStartSec(), s.getEndSec(), s.getDurationSec(),
                            s.getStudyCount(),
                            meta.sentenceKo(), meta.keyExpressions(), meta.difficulty(),
                            meta.difficultyScore(), meta.vocabulary(), meta.wordTimestamps()
                    );
                })
                .toList();

        return new StudySessionCreateResponse(
                newSession.getId(),
                original.getId(),
                new StudySessionCreateResponse.VideoData(
                        video.getVideoId(),
                        video.getEmbedUrl(),
                        "https://www.youtube.com/watch?v=" + video.getVideoId(),
                        video.getTitle(),
                        video.getThumbnailUrl(),
                        video.getDuration(),
                        video.getChannelTitle()
                ),
                sentencesData,
                false
        );
    }

    @Transactional
    public void deleteStudySession(Long sessionId, Long userId) {
        StudySession session = studySessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));

        if (session.getStatus() == SessionStatus.DELETED) {
            throw new CustomException(ErrorCode.SESSION_NOT_FOUND);
        }

        if (!session.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        session.delete();
    }

    @Transactional
    public void startReview(Long sessionId, Long userId) {
        StudySession session = studySessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));

        if (session.getStatus() == SessionStatus.DELETED) {
            throw new CustomException(ErrorCode.SESSION_NOT_FOUND);
        }

        if (!session.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        session.startReview();
    }

    private record SentenceMetadata(
            String sentenceKo,
            List<String> keyExpressions,
            String difficulty,
            Double difficultyScore,
            List<PythonGenerateReferenceResponse.Vocabulary> vocabulary,
            List<PythonGenerateReferenceResponse.WordTimestamp> wordTimestamps
    ) {}

    private SentenceMetadata parseSentenceMetadata(Sentence sentence) {
        try {
            List<String> keyExpressions = sentence.getKeyExpressionsJson() != null
                    ? objectMapper.readValue(sentence.getKeyExpressionsJson(), new TypeReference<>() {})
                    : null;
            List<PythonGenerateReferenceResponse.Vocabulary> vocabulary = sentence.getVocabularyJson() != null
                    ? objectMapper.readValue(sentence.getVocabularyJson(), new TypeReference<>() {})
                    : null;
            List<PythonGenerateReferenceResponse.WordTimestamp> wordTimestamps = sentence.getWordTimestamps() != null
                    ? objectMapper.readValue(sentence.getWordTimestamps(), new TypeReference<>() {})
                    : null;
            return new SentenceMetadata(sentence.getSentenceKo(), keyExpressions,
                    sentence.getDifficulty(), sentence.getDifficultyScore(), vocabulary, wordTimestamps);
        } catch (Exception e) {
            log.warn("sentence 메타데이터 파싱 실패. sentenceId={}", sentence.getId(), e);
            return new SentenceMetadata(null, null, null, null, null, null);
        }
    }

    private String buildHiddenSentence(Long sessionId, Long sentenceId, String content) {
        // 1. 진행 중인 사이클의 step-2 평가를 Redis에서 먼저 조회
        //    (step-2 평가가 아직 DB에 커밋되기 전이므로 Redis 우선 확인)
        Optional<PendingEvaluation> step2 = pendingEvaluationStore.findStep(sessionId, sentenceId, 2);
        if (step2.isPresent()) {
            return maskFromWordLevelFeedback(sentenceId, step2.get().getWordLevelFeedback(), content);
        }

        // 2. Redis에 없으면 DB의 최신 평가 사용 (이전 사이클)
        return evaluationRepository.findTopBySentence_IdOrderByCreatedAtDesc(sentenceId)
                .map(eval -> maskFromWordLevelFeedback(sentenceId, eval.getWordLevelFeedback(), content))
                .orElseGet(() -> WordMasker.mask(content));
    }

    private String maskFromWordLevelFeedback(Long sentenceId, String json, String content) {
        try {
            JsonNode feedbackList = objectMapper.readTree(json);
            Set<String> wordsToMask = new HashSet<>();
            for (JsonNode item : feedbackList) {
                if (!"good".equals(item.path("status").asText())) {
                    wordsToMask.add(item.path("word").asText().toLowerCase());
                }
            }
            return WordMasker.mask(content, wordsToMask);
        } catch (Exception e) {
            log.warn("word_level_feedback 파싱 실패, 랜덤 마스킹으로 폴백. sentenceId={}", sentenceId, e);
            return WordMasker.mask(content);
        }
    }

}
