package com.bremenband.shadowengapi.domain.game.entity;

import com.bremenband.shadowengapi.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "game_sentences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class GameSentence extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int level; // 1, 2, 3

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String features; // JSON, Python API용

    @Column(columnDefinition = "TEXT")
    private String wordTimestamps; // JSON, Python API용

    @Column(columnDefinition = "TEXT")
    private String maskedContent; // round 2용 사전 마스킹 문장

    @Column
    private String referenceAudioUrl; // S3 URL
}
