package com.bremenband.shadowengapi.domain.game.entity;

import com.bremenband.shadowengapi.domain.user.entity.User;
import com.bremenband.shadowengapi.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "game_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class GameRecord extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private int level;

    @Column(nullable = false)
    private LocalDate playedDate;

    @Column(nullable = false)
    private int hearts; // 0~3

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal avgTotalScore;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal avgSpeedSimilarity;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal avgDynamicStressScore;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal avgBoundaryToneScore;

    // 평균 총점 × (1 + 하트 수 × 0.1)
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal finalScore;

    // 모든 회차 total_score 합산
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal cumulativeScore;
}
