package com.bremenband.shadowengapi.domain.youtube.service;

import com.bremenband.shadowengapi.domain.youtube.client.YoutubeApiClient;
import com.bremenband.shadowengapi.domain.youtube.dto.res.VideoInfoResponse;
import com.bremenband.shadowengapi.domain.youtube.dto.YoutubeApiResponse;
import com.bremenband.shadowengapi.global.exception.CustomException;
import com.bremenband.shadowengapi.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class YoutubeService {

    private static final Pattern VIDEO_ID_PATTERN = Pattern.compile(
            "(?:youtube\\.com/watch\\?.*v=|youtu\\.be/|youtube\\.com/embed/|youtube\\.com/shorts/)([\\w-]{11})"
    );

    private final YoutubeApiClient youtubeApiClient;

    public VideoInfoResponse getVideo(String url) {
        String videoId = extractVideoId(url);

        YoutubeApiResponse apiResponse = youtubeApiClient.fetchVideoInfo(videoId);

        List<YoutubeApiResponse.YoutubeItem> items = apiResponse.items();
        if (items == null || items.isEmpty()) {
            throw new CustomException(ErrorCode.VIDEO_NOT_FOUND);
        }

        YoutubeApiResponse.YoutubeItem item = items.get(0);
        YoutubeApiResponse.YoutubeSnippet snippet = item.snippet();
        YoutubeApiResponse.YoutubeContentDetails contentDetails = item.contentDetails();

        String thumbnailUrl = extractThumbnailUrl(snippet.thumbnails(), videoId);
        long durationSec = Duration.parse(contentDetails.duration()).getSeconds();

        return new VideoInfoResponse(
                videoId,
                "https://www.youtube.com/embed/" + videoId,
                snippet.title(),
                thumbnailUrl,
                durationSec,
                snippet.channelTitle()
        );
    }

    public String extractVideoId(String url) {
        Matcher matcher = VIDEO_ID_PATTERN.matcher(url);
        if (!matcher.find()) {
            throw new CustomException(ErrorCode.INVALID_YOUTUBE_URL);
        }
        return matcher.group(1);
    }

    private String extractThumbnailUrl(YoutubeApiResponse.YoutubeThumbnails thumbnails, String videoId) {
        if (thumbnails.maxres() != null) return thumbnails.maxres().url();
        if (thumbnails.standard() != null) return thumbnails.standard().url();
        if (thumbnails.high() != null) return thumbnails.high().url();
        return "https://i.ytimg.com/vi/" + videoId + "/maxresdefault.jpg";
    }
}
