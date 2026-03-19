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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    @Transactional
    public void completeSessionIfAllEvaluated(Long sessionId) {
        long total     = sentenceRepository.countByStudySession_Id(sessionId);
        long completed = evaluationRepository.countDistinctEvaluatedSentencesBySessionId(sessionId);

        if (total > 0 && completed >= total) {
            StudySession session = studySessionRepository.findById(sessionId)
                    .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));
            session.complete();
        }
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
                        .metadataUrl(t.metadataUrl())
                        .build())
                .toList();
        List<Sentence> sentences = sentenceRepository.saveAll(sentencesToSave);

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
                sentences.stream()
                        .map(s -> new StudySessionCreateResponse.SentenceData(
                                s.getId(),
                                s.getContent(),
                                s.getStartSec(),
                                s.getEndSec(),
                                s.getDurationSec(),
                                0
                        ))
                        .toList()
        );
    }
}
