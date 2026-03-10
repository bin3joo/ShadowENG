package com.bremenband.shadowengapi.domain.study.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "sentences")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Sentence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private StudySession studySession;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "start_sec", nullable = false)
    private double startSec;

    @Column(name = "end_sec", nullable = false)
    private double endSec;

    @Column(name = "duration_sec", nullable = false)
    private double durationSec;

    // evaluate-audio 호출 시 레퍼런스로 사용
    @Column(name = "word_timestamps", columnDefinition = "json")
    private String wordTimestamps;

    // evaluate-audio 호출 시 레퍼런스로 사용
    @Column(name = "features", columnDefinition = "json")
    private String features;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private Sentence(StudySession studySession, String content,
                     double startSec, double endSec, double durationSec,
                     String wordTimestamps, String features) {
        this.studySession = studySession;
        this.content = content;
        this.startSec = startSec;
        this.endSec = endSec;
        this.durationSec = durationSec;
        this.wordTimestamps = wordTimestamps;
        this.features = features;
    }
}
