package com.bremenband.shadowengapi.domain.study.entity;

import com.bremenband.shadowengapi.domain.user.entity.User;
import com.bremenband.shadowengapi.domain.youtube.entity.Video;
import com.bremenband.shadowengapi.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "study_sessions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudySession extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id", nullable = false)
    private Video video;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "start_sec", nullable = false)
    private double startSec;

    @Column(name = "end_sec", nullable = false)
    private double endSec;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionStatus status = SessionStatus.ACTIVE;

    @Column(name = "cycle_count", nullable = false)
    private int cycleCount = 0;

    @Column(name = "is_reviewing", nullable = false)
    private boolean isReviewing = false;

    public void complete() {
        this.status = SessionStatus.COMPLETED;
    }

    public void delete() {
        this.status = SessionStatus.DELETED;
    }

    public void startReview() {
        this.isReviewing = true;
    }

    public void completeCycle() {
        this.cycleCount++;
        this.isReviewing = false;
    }

    @Builder
    private StudySession(Video video, User user, double startSec, double endSec) {
        this.video = video;
        this.user = user;
        this.startSec = startSec;
        this.endSec = endSec;
        this.status = SessionStatus.ACTIVE;
        this.cycleCount = 0;
        this.isReviewing = false;
    }
}
