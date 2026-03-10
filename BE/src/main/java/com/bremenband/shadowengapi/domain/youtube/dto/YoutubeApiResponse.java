package com.bremenband.shadowengapi.domain.youtube.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record YoutubeApiResponse(
        List<YoutubeItem> items
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record YoutubeItem(
            String id,
            YoutubeSnippet snippet,
            YoutubeContentDetails contentDetails
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record YoutubeSnippet(
            String title,
            String channelTitle,
            YoutubeThumbnails thumbnails
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record YoutubeThumbnails(
            YoutubeThumbnailInfo maxres,
            YoutubeThumbnailInfo standard,
            YoutubeThumbnailInfo high,
            YoutubeThumbnailInfo medium,
            @JsonProperty("default") YoutubeThumbnailInfo defaultThumbnail
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record YoutubeThumbnailInfo(
            String url,
            int width,
            int height
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record YoutubeContentDetails(
            String duration
    ) {}
}
