package com.bremenband.shadowengapi.domain.study.dto.python;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PythonEvaluateAudioResponse(

        String status,

        String message,

        @JsonProperty("user_transcription")
        String userTranscription,

        Details details,

        Scores scores

) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Details(

            @JsonProperty("word_level_feedback")
            List<WordLevelFeedback> wordLevelFeedback,

            @JsonProperty("boundary_tone_feedback")
            BoundaryToneFeedback boundaryToneFeedback,

            @JsonProperty("dynamic_stress_feedback")
            DynamicStressFeedback dynamicStressFeedback,

            @JsonProperty("pitch_contour_feedback")
            List<PitchContourFeedback> pitchContourFeedback

    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WordLevelFeedback(
            String word,
            String status,
            @JsonProperty("ref_start_time") Double refStartTime,
            @JsonProperty("ref_end_time") Double refEndTime,
            @JsonProperty("user_start_time") Double userStartTime,
            @JsonProperty("user_end_time") Double userEndTime
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BoundaryToneFeedback(
            @JsonProperty("ref_slope") Double refSlope,
            @JsonProperty("user_slope") Double userSlope,
            String status
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DynamicStressFeedback(
            @JsonProperty("ref_dynamic_ratio") Double refDynamicRatio,
            @JsonProperty("user_dynamic_ratio") Double userDynamicRatio,
            String status
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PitchContourFeedback(
            String word,
            @JsonProperty("ref_direction") String refDirection,
            @JsonProperty("ref_start_hz") Double refStartHz,
            @JsonProperty("ref_end_hz") Double refEndHz,
            @JsonProperty("user_direction") String userDirection,
            @JsonProperty("user_start_hz") Double userStartHz,
            @JsonProperty("user_end_hz") Double userEndHz,
            String feedback
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Scores(
            @JsonProperty("total_score") double totalScore,
            @JsonProperty("word_accuracy") double wordAccuracy,
            @JsonProperty("prosody_and_stress") double prosodyAndStress,
            @JsonProperty("word_rhythm_score") double wordRhythmScore,
            @JsonProperty("boundary_tone_score") double boundaryToneScore,
            @JsonProperty("dynamic_stress_score") double dynamicStressScore,
            @JsonProperty("speed_similarity") double speedSimilarity,
            @JsonProperty("pause_similarity") double pauseSimilarity
    ) {
    }
}
