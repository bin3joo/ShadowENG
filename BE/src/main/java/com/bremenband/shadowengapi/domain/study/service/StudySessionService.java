package com.bremenband.shadowengapi.domain.study.service;

import com.bremenband.shadowengapi.domain.study.dto.req.StudySessionCreateRequest;
import com.bremenband.shadowengapi.domain.study.dto.res.ActiveSessionResponse;
import com.bremenband.shadowengapi.domain.study.dto.res.ActiveSessionsResponse;
import com.bremenband.shadowengapi.domain.study.dto.res.LatestActiveSessionResponse;
import com.bremenband.shadowengapi.domain.study.dto.res.RecentStudySessionResponse;
import com.bremenband.shadowengapi.domain.study.dto.res.StudySessionCreateResponse;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;


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
    private final StudySessionWriter studySessionWriter;

    public ActiveSessionsResponse getActiveSessions(Long userId) {
        List<StudySession> sessions = studySessionRepository
                .findByUser_IdAndStatusOrderByCreatedAtDesc(userId, SessionStatus.ACTIVE);

        List<ActiveSessionResponse> list = sessions.stream()
                .map(session -> {
                    String videoId = session.getVideo().getVideoId();
                    ThumbnailInfo thumbnail = new ThumbnailInfo(
                            "https://i.ytimg.com/vi/" + videoId + "/sddefault.jpg", 640, 480);
                    return new ActiveSessionResponse(session.getId(), thumbnail, session.getProgressRate());
                })
                .toList();

        return new ActiveSessionsResponse(list);
    }

    public StudySessionCreateResponse getStudySession(Long sessionId, Long userId) {
        StudySession session = studySessionRepository.findByIdWithVideo(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));

        if (!session.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        Video video = session.getVideo();
        List<Sentence> sentences = sentenceRepository.findByStudySession_Id(sessionId);

        // 문장별 평가 횟수를 한 번의 쿼리로 집계
        Map<Long, Long> evalCountMap = evaluationRepository.findByStudySession_Id(sessionId)
                .stream()
                .collect(Collectors.groupingBy(e -> e.getSentence().getId(), Collectors.counting()));

        List<StudySessionCreateResponse.SentenceData> sentencesData = sentences.stream()
                .map(s -> new StudySessionCreateResponse.SentenceData(
                        s.getId(),
                        s.getContent(),
                        s.getStartSec(),
                        s.getEndSec(),
                        s.getDurationSec(),
                        evalCountMap.getOrDefault(s.getId(), 0L).intValue()
                ))
                .toList();

        return new StudySessionCreateResponse(
                session.getId(),
                new StudySessionCreateResponse.VideoData(
                        video.getVideoId(),
                        video.getEmbedUrl(),
                        video.getTitle(),
                        video.getThumbnailUrl(),
                        video.getDuration(),
                        video.getChannelTitle()
                ),
                sentencesData
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

}
