package com.bremenband.shadowengapi.domain.game.service;

import com.bremenband.shadowengapi.domain.game.entity.GameSentence;
import com.bremenband.shadowengapi.domain.game.repository.GameSentenceRepository;
import com.bremenband.shadowengapi.domain.study.dto.python.PythonGenerateReferenceResponse;
import com.bremenband.shadowengapi.domain.study.util.WordMasker;
import com.bremenband.shadowengapi.global.s3.S3Uploader;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 서버 시작 시 game_sentences 테이블이 비어있으면
 * S3의 game/{difficulty}/{sentenceId}/ 구조에서 데이터를 읽어 DB에 저장한다.
 *
 * S3 구조:
 *   game/easy/{sentenceId}/full_audio.wav
 *   game/easy/{sentenceId}/{sentenceId}.json   ← PythonGenerateReferenceResponse 포맷
 *   game/normal/...
 *   game/hard/...
 *
 * difficulty → level: easy=1, normal=2, hard=3
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GameSentenceSeeder implements ApplicationRunner {

    private final GameSentenceRepository gameSentenceRepository;
    private final S3Uploader s3Uploader;
    private final ObjectMapper objectMapper;

    private static final Map<String, Integer> DIFFICULTY_LEVEL = Map.of(
            "easy",   1,
            "normal", 2,
            "hard",   3
    );

    @Override
    public void run(ApplicationArguments args) {
        if (gameSentenceRepository.count() > 0) {
            log.info("[GameSentenceSeeder] already seeded, skipping");
            return;
        }

        List<GameSentence> sentences = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : DIFFICULTY_LEVEL.entrySet()) {
            String difficulty = entry.getKey();
            int    level      = entry.getValue();

            List<String> folders = s3Uploader.listFolders("game/" + difficulty + "/");
            log.info("[GameSentenceSeeder] difficulty={}, folders={}", difficulty, folders.size());

            for (String folder : folders) {
                // folder 예: "game/easy/p230_305/"
                String sentenceId = folder.split("/")[2];
                String jsonKey    = folder + sentenceId + ".json";
                String audioKey   = folder + "full_audio.wav";

                try {
                    GameSentence sentence = buildSentence(level, jsonKey, audioKey);
                    sentences.add(sentence);
                    log.info("[GameSentenceSeeder] parsed: level={}, sentenceId={}", level, sentenceId);
                } catch (Exception e) {
                    log.error("[GameSentenceSeeder] failed: folder={}", folder, e);
                }
            }
        }

        gameSentenceRepository.saveAll(sentences);
        log.info("[GameSentenceSeeder] seeded total={}", sentences.size());
    }

    private GameSentence buildSentence(int level, String jsonKey, String audioKey)
            throws JsonProcessingException {

        String raw = s3Uploader.fetchJson(jsonKey);
        PythonGenerateReferenceResponse response =
                objectMapper.readValue(raw, PythonGenerateReferenceResponse.class);

        PythonGenerateReferenceResponse.Part part = response.parts().get(0);

        String content          = part.sentence();
        String wordTimestamps   = objectMapper.writeValueAsString(part.wordTimestamps());
        String featuresJson     = objectMapper.writeValueAsString(part.features());
        String metadataJson     = objectMapper.writeValueAsString(buildMetadata(part));
        String maskedContent    = WordMasker.mask(content);

        String featuresUrl  = s3Uploader.uploadJson(featuresJson,  "features/");
        String metadataUrl  = s3Uploader.uploadJson(metadataJson,  "metadata/");

        return GameSentence.builder()
                .level(level)
                .content(content)
                .maskedContent(maskedContent)
                .wordTimestamps(wordTimestamps)
                .featuresUrl(featuresUrl)
                .metadataUrl(metadataUrl)
                .referenceAudioUrl(audioKey)
                .build();
    }

    private Map<String, Object> buildMetadata(PythonGenerateReferenceResponse.Part part) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("sentenceKo",      part.sentenceKo());
        map.put("keyExpressions",  part.keyExpressions());
        map.put("difficulty",      part.difficulty());
        map.put("difficultyScore", part.difficultyScore());
        map.put("vocabulary",      part.vocabulary());
        return map;
    }
}
