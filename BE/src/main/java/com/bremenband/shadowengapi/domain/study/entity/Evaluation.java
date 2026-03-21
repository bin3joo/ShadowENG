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

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "evaluations")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Evaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private StudySession studySession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sentence_id", nullable = false)
    private Sentence sentence;

    @Column(name = "user_transcription", nullable = false, columnDefinition = "TEXT")
    private String userTranscription;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "word_level_feedback", nullable = false, columnDefinition = "json")
    private String wordLevelFeedback;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "boundary_tone_feedback", nullable = false, columnDefinition = "json")
    private String boundaryToneFeedback;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dynamic_stress_feedback", nullable = false, columnDefinition = "json")
    private String dynamicStressFeedback;

    @Column(name = "step", nullable = false)
    private int step;

    @Column(name = "total_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal totalScore;

    @Column(name = "word_accuracy", nullable = false, precision = 5, scale = 2)
    private BigDecimal wordAccuracy;

    @Column(name = "prosody_and_stress", nullable = false, precision = 5, scale = 2)
    private BigDecimal prosodyAndStress;

    @Column(name = "word_rhythm_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal wordRhythmScore;

    @Column(name = "boundary_tone_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal boundaryToneScore;

    @Column(name = "dynamic_stress_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal dynamicStressScore;

    @Column(name = "speed_similarity", nullable = false, precision = 5, scale = 2)
    private BigDecimal speedSimilarity;

    @Column(name = "pause_similarity", nullable = false, precision = 5, scale = 2)
    private BigDecimal pauseSimilarity;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private Evaluation(StudySession studySession, Sentence sentence, int step, String userTranscription,
                       String wordLevelFeedback, String boundaryToneFeedback, String dynamicStressFeedback,
                       BigDecimal totalScore, BigDecimal wordAccuracy, BigDecimal prosodyAndStress,
                       BigDecimal wordRhythmScore, BigDecimal boundaryToneScore, BigDecimal dynamicStressScore,
                       BigDecimal speedSimilarity, BigDecimal pauseSimilarity) {
        this.studySession = studySession;
        this.sentence = sentence;
        this.step = step;
        this.userTranscription = userTranscription;
        this.wordLevelFeedback = wordLevelFeedback;
        this.boundaryToneFeedback = boundaryToneFeedback;
        this.dynamicStressFeedback = dynamicStressFeedback;
        this.totalScore = totalScore;
        this.wordAccuracy = wordAccuracy;
        this.prosodyAndStress = prosodyAndStress;
        this.wordRhythmScore = wordRhythmScore;
        this.boundaryToneScore = boundaryToneScore;
        this.dynamicStressScore = dynamicStressScore;
        this.speedSimilarity = speedSimilarity;
        this.pauseSimilarity = pauseSimilarity;
    }
}
