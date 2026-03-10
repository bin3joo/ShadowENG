package com.bremenband.shadowengapi.domain.youtube.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "videos")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Video {

    @Id
    @Column(name = "video_id", length = 50)
    private String videoId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "embed_url", nullable = false, length = 255)
    private String embedUrl;

    @Column(name = "thumbnail_url", length = 255)
    private String thumbnailUrl;

    @Column(nullable = false)
    private int duration;

    @Column(name = "channel_title", nullable = false, length = 255)
    private String channelTitle;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private Video(String videoId, String title, String embedUrl, String thumbnailUrl,
                  int duration, String channelTitle) {
        this.videoId = videoId;
        this.title = title;
        this.embedUrl = embedUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.duration = duration;
        this.channelTitle = channelTitle;
    }
}
