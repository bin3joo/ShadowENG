package com.bremenband.shadowengapi.domain.game.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "daily_best_records")
@IdClass(DailyBestRecordId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class DailyBestRecord {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "level")
    private int level;

    @Id
    @Column(name = "record_date")
    private LocalDate recordDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_record_id")
    private GameRecord gameRecord;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal bestFinalScore;

    public void update(GameRecord newRecord, BigDecimal newScore) {
        this.gameRecord = newRecord;
        this.bestFinalScore = newScore;
    }
}
