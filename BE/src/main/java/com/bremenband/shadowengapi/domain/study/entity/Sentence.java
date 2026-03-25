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

    @Column(name = "sentence_ko", columnDefinition = "TEXT")
    private String sentenceKo;

    @Column(name = "difficulty")
    private String difficulty;

    @Column(name = "difficulty_score")
    private Double difficultyScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "key_expressions", columnDefinition = "json")
    private String keyExpressionsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "vocabulary_json", columnDefinition = "json")
    private String vocabularyJson;

    @Column(name = "study_count", nullable = false, columnDefinition = "integer default 0")
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
                     String wordTimestamps, String featuresUrl,
                     String sentenceKo, String difficulty, Double difficultyScore,
                     String keyExpressionsJson, String vocabularyJson) {
        this.studySession = studySession;
        this.content = content;
        this.startSec = startSec;
        this.endSec = endSec;
        this.durationSec = durationSec;
        this.wordTimestamps = wordTimestamps;
        this.featuresUrl = featuresUrl;
        this.sentenceKo = sentenceKo;
        this.difficulty = difficulty;
        this.difficultyScore = difficultyScore;
        this.keyExpressionsJson = keyExpressionsJson;
        this.vocabularyJson = vocabularyJson;
        this.studyCount = 0;
    }
}
