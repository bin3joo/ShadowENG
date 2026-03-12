package com.bremenband.shadowengapi.domain.study.controller;

import com.bremenband.shadowengapi.domain.study.dto.req.EvaluationRequest;
import com.bremenband.shadowengapi.domain.study.dto.req.StudySessionCreateRequest;
import com.bremenband.shadowengapi.domain.study.dto.res.ActiveSessionsResponse;
import com.bremenband.shadowengapi.domain.study.dto.res.EvaluationResponse;
import com.bremenband.shadowengapi.domain.study.dto.res.RecentStudySessionResponse;
import com.bremenband.shadowengapi.domain.study.dto.res.SentenceLearningResponse;
import com.bremenband.shadowengapi.domain.study.dto.res.StudySessionCreateResponse;
import com.bremenband.shadowengapi.domain.study.service.EvaluationService;
import com.bremenband.shadowengapi.domain.study.service.StudySessionService;
import org.springframework.web.multipart.MultipartFile;
import com.bremenband.shadowengapi.global.common.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping(value = "/study-sessions", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "학습세션 API", description = "학습 세션 관련 기능을 위한 REST API")
public class StudySessionController {

    private final StudySessionService studySessionService;
    private final EvaluationService evaluationService;

    @GetMapping
    @Operation(
            summary = "사용자가 학습 중인 세션 목록 조회",
            description = "요청 헤더의 Access Token을 통해 인증된 사용자의 학습 중 세션 전체 목록을 조회합니다."
    )
    public ApiResponse<ActiveSessionsResponse> getStudySessions(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.success(studySessionService.getActiveSessions(userId));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "학습 세션 생성",
            description = "embedUrl과 학습 구간(startSec, endSec)을 전달받아 세션을 생성하고 전사된 문장 목록을 반환합니다."
    )
    public ApiResponse<StudySessionCreateResponse> createStudySession(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Valid @RequestBody StudySessionCreateRequest request
    ) {
        return ApiResponse.success(studySessionService.createStudySession(userId, request));
    }

    @GetMapping("/recent")
    @Operation(
            summary = "최근 학습 세션 조회",
            description = "인증된 사용자의 가장 최근 학습 세션 정보를 조회합니다."
    )
    public ApiResponse<RecentStudySessionResponse> getRecentSession(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.success(studySessionService.getRecentSession(userId));
    }

    @PostMapping(value = "/{sessionId}/evaluations", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "사용자의 음성 데이터 전송",
            description = "녹음한 음성 파일과 평가 대상 문장 ID를 전달받아 Python AI 서버에서 발화를 분석하고 결과를 반환합니다."
    )
    public ApiResponse<EvaluationResponse> sendVoice(
            @Parameter(description = "학습 세션 ID", example = "1234")
            @PathVariable Long sessionId,
            @Parameter(description = "평가 대상 문장 ID", example = "1234")
            @RequestParam Long sentenceId,
            @Parameter(description = "녹음된 음성 파일 (wav, m4a 등)")
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "학습 단계 정보 (JSON)")
            @RequestPart("request") @Valid EvaluationRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.success(evaluationService.evaluate(sessionId, sentenceId, request.step(), file, userId));
    }

    @GetMapping("/{sessionId}")
    @Operation(
            summary = "단일 학습 세션 세부 조회",
            description = "세션 ID로 학습 세션의 영상 정보와 문장 목록(학습 횟수 포함)을 조회합니다."
    )
    public ApiResponse<StudySessionCreateResponse> getStudySession(
            @Parameter(description = "조회할 학습 세션의 고유 ID", example = "1234")
            @PathVariable Long sessionId,
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.success(studySessionService.getStudySession(sessionId, userId));
    }

    @GetMapping("/{sessionId}/sentences/{sentenceId}")
    @Operation(
            summary = "특정 문장 학습 데이터 조회",
            description = "학습 단계(step)에 따라 문장 원문 또는 마스킹된 문장을 반환합니다. " +
                    "첫 학습(studyCount=0)이면 step 1(hiddenSentence=null), " +
                    "재학습이면 step 2(hiddenSentence에 마스킹된 문장 포함)."
    )
    public ApiResponse<SentenceLearningResponse> getSentenceLearning(
            @Parameter(description = "학습 세션 ID", example = "12345")
            @PathVariable Long sessionId,
            @Parameter(description = "문장 ID", example = "1234")
            @PathVariable Long sentenceId,
            @Parameter(description = "학습 단계 (1~4)", example = "1")
            @RequestParam @Min(1) @Max(4) int step,
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.success(studySessionService.getSentenceLearning(sessionId, sentenceId, step, userId));
    }

}
