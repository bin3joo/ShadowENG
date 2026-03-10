package com.bremenband.shadowengapi.domain.youtube.client;

import com.bremenband.shadowengapi.domain.youtube.dto.YoutubeApiResponse;
import com.bremenband.shadowengapi.global.exception.CustomException;
import com.bremenband.shadowengapi.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class YoutubeApiClient {

    private final RestClient restClient;

    @Value("${youtube.api.key}")
    private String apiKey;

    @Value("${youtube.api.base-url}")
    private String baseUrl;

    public YoutubeApiResponse fetchVideoInfo(String videoId) {
        try {
            return restClient.get()
                    .uri(baseUrl + "/videos?part=snippet,contentDetails&id={videoId}&key={key}",
                            videoId, apiKey)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new CustomException(ErrorCode.EXTERNAL_API_ERROR);
                    })
                    .body(YoutubeApiResponse.class);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            throw new CustomException(ErrorCode.EXTERNAL_API_ERROR);
        }
    }
}
