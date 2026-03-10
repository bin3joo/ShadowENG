package com.bremenband.shadowengapi.domain.report.entity;

import com.bremenband.shadowengapi.domain.study.entity.StudySession;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "reports")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false, unique = true)
    private StudySession studySession;

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
    private Report(StudySession studySession, BigDecimal totalScore, BigDecimal wordAccuracy,
                   BigDecimal prosodyAndStress, BigDecimal wordRhythmScore, BigDecimal boundaryToneScore,
                   BigDecimal dynamicStressScore, BigDecimal speedSimilarity, BigDecimal pauseSimilarity) {
        this.studySession = studySession;
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
