package com.bremenband.shadowengapi.global.common;

import com.bremenband.shadowengapi.global.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
@Schema(description = "API 공통 응답 DTO")
public class ApiResponse<T> {

    @Schema(description = "응답 코드", example = "200")
    private final int code;

    @Schema(description = "응답 메시지", example = "요청에 성공하였습니다.")
    private final String message;

    @Schema(description = "응답 데이터")
    private final T data;

    @Schema(description = "성공 여부", example = "true")
    private final Boolean isSuccess;

    // 성공 시
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "요청에 성공하였습니다.", data, true);
    }

    // 성공이지만 데이터는 없을 때
    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(200, "요청에 성공하였습니다.", null, true);
    }

    // 실패 시 (ErrorCode 사용)
    public static <T> ApiResponse<T> fail(ErrorCode errorCode) {
        // ErrorCode 클래스 내부에 정의된 상태 코드(int)를 반환하는 메서드(예: getStatus(), getCode() 등)로 수정하여 사용하시면 됩니다.
        return new ApiResponse<>(errorCode.getCode(), errorCode.getMessage(), null, false);
    }

    // 실패 시 (메시지 직접 입력)
    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String message) {
        return new ApiResponse<>(errorCode.getCode(), message, null, false);
    }

    private ApiResponse(int code, String message, T data, boolean isSuccess) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.isSuccess = isSuccess;
    }
}