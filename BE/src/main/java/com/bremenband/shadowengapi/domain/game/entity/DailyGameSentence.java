package com.bremenband.shadowengapi.domain.game.entity;

import com.bremenband.shadowengapi.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "daily_game_sentences",
        uniqueConstraints = @UniqueConstraint(columnNames = {"level", "assigned_date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class DailyGameSentence extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int level;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_sentence_id", nullable = false)
    private GameSentence gameSentence;

    @Column(name = "assigned_date", nullable = false)
    private LocalDate assignedDate;
}
