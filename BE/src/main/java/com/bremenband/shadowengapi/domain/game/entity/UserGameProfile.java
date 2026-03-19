package com.bremenband.shadowengapi.domain.game.entity;

import com.bremenband.shadowengapi.domain.user.entity.User;
import com.bremenband.shadowengapi.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "user_game_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class UserGameProfile extends BaseTimeEntity {

    @Id
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Tier tier;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal weeklyScore;

    @Column(nullable = false)
    private int consecutiveNoPlayWeeks;

    @Column(nullable = false)
    private boolean frozen;

    // 마지막으로 플레이한 주의 월요일 (null이면 한 번도 플레이 안 함)
    @Column
    private LocalDate lastPlayedWeek;

    public void promote() {
        this.tier = this.tier.promote();
    }

    public void demote() {
        if (!this.tier.isLowest()) {
            this.tier = this.tier.demote();
        }
    }

    public void addWeeklyScore(BigDecimal score) {
        this.weeklyScore = this.weeklyScore.add(score);
    }

    public void resetWeeklyScore() {
        this.weeklyScore = BigDecimal.ZERO;
    }

    /**
     * 게임 플레이 시 호출: 미플레이 주 초기화, freeze 해제.
     */
    public void markPlayedThisWeek(LocalDate weekStart) {
        this.lastPlayedWeek = weekStart;
        this.consecutiveNoPlayWeeks = 0;
        this.frozen = false;
    }

    /**
     * 매주 월요일 자정 스케줄러에서 호출.
     * 3주까지는 1단계 강등, 4주 이상이면 freeze (강등 없음).
     */
    public void applyNoPlayPenalty() {
        this.consecutiveNoPlayWeeks++;
        if (this.consecutiveNoPlayWeeks >= 4) {
            this.frozen = true;
        } else if (!this.tier.isLowest()) {
            this.tier = this.tier.demote();
        }
    }
}
