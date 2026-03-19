package com.bremenband.shadowengapi.domain.study.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
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
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "word_timestamps", columnDefinition = "json")
    private String wordTimestamps;

    // S3에 저장된 features JSON의 키 (f0_array, rms_array) — evaluate-audio 호출 시 fetch
    @Column(name = "features_url")
    private String featuresUrl;

    // S3에 저장된 메타데이터 JSON의 키 (vocabulary, sentenceKo 등) — 향후 사용 예정
    @Column(name = "metadata_url")
    private String metadataUrl;

    @Column(name = "study_count", nullable = false)
    private int studyCount = 0;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public void incrementStudyCount() {
        this.studyCount++;
    }

    @Builder
    private Sentence(StudySession studySession, String content,
                     double startSec, double endSec, double durationSec,
                     String wordTimestamps, String featuresUrl, String metadataUrl) {
        this.studySession = studySession;
        this.content = content;
        this.startSec = startSec;
        this.endSec = endSec;
        this.durationSec = durationSec;
        this.wordTimestamps = wordTimestamps;
        this.featuresUrl = featuresUrl;
        this.metadataUrl = metadataUrl;
        this.studyCount = 0;
    }
}
