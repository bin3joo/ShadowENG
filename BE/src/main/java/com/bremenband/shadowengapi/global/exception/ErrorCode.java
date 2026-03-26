package com.bremenband.shadowengapi.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    //1001, 1002, 1003, ..., 2001, 2002, ... 식으로 천 단위로 커스텀 에러코드를 지정
    //에러코드를 아래로 계속 추가

    // [1000 ~] : 학습 관련 에러
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, 1000, "올바르지 않은 요청입니다."),
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, 1001, "입력값이 올바르지 않습니다."),
    INVALID_YOUTUBE_URL(HttpStatus.BAD_REQUEST, 1002, "올바르지 않은 유튜브 URL입니다."),
    VOICE_RECOGNITION_FAILED(HttpStatus.BAD_REQUEST, 1003, "음성이 인식되지 않았습니다. 다시 녹음해주세요."),
    AUDIO_TOO_SHORT(HttpStatus.BAD_REQUEST, 1009, "녹음 시간이 너무 짧습니다. 다시 녹음해주세요."),
    NO_EVALUATIONS_FOR_REPORT(HttpStatus.BAD_REQUEST, 1004, "평가 결과가 없어 리포트를 생성할 수 없습니다."),
    GAME_ROUND_INVALID(HttpStatus.BAD_REQUEST, 1005, "유효하지 않은 게임 라운드 요청입니다. 이전 라운드를 먼저 완료해주세요."),
    PREVIOUS_STEP_NOT_COMPLETED(HttpStatus.BAD_REQUEST, 1006, "이전 학습 단계를 먼저 완료해주세요."),
    REVIEW_NOT_STARTED(HttpStatus.BAD_REQUEST, 1007, "복습 모드가 활성화되지 않았습니다. 복습하기를 먼저 눌러주세요."),
    SESSION_NOT_COMPLETED(HttpStatus.BAD_REQUEST, 1008, "학습이 완료된 세션에서만 재학습을 시작할 수 있습니다."),

    // [2000 ~] : DB 관련 에러
    DATA_NOT_FOUND(HttpStatus.NOT_FOUND, 2000, "조회된 데이터가 없습니다"),
    CSV_FILE_LOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, 2001, "서버의 데이터 파일을 읽어오는 데 실패했습니다"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, 2002, "사용자를 찾을 수 없습니다."),
    VIDEO_NOT_FOUND(HttpStatus.NOT_FOUND, 2003, "해당 영상을 찾을 수 없습니다."),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, 2004, "학습 세션을 찾을 수 없습니다."),
    SENTENCE_NOT_FOUND(HttpStatus.NOT_FOUND, 2005, "문장을 찾을 수 없습니다."),
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, 2006, "리포트를 찾을 수 없습니다."),
    GAME_PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, 2007, "게임 프로필이 없습니다. 먼저 게임을 플레이해주세요."),
    GAME_DAILY_SENTENCE_NOT_ASSIGNED(HttpStatus.SERVICE_UNAVAILABLE, 2008, "오늘의 게임 문장이 아직 배정되지 않았습니다."),

    // [3000 ~] : 인증/토큰 에러
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, 3000, "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, 3001, "만료된 토큰입니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, 3002, "리프레시 토큰이 올바르지 않습니다."),

    // [4000 ~] : 게임 관련 에러
    FORBIDDEN(HttpStatus.FORBIDDEN, 4001, "접근 권한이 없습니다."),
    USER_IS_FROZEN(HttpStatus.FORBIDDEN, 4002, "동결(freeze) 상태에서는 리더보드에 접근할 수 없습니다."),
    GAME_LEVEL_LOCKED(HttpStatus.FORBIDDEN, 4003, "잠긴 레벨입니다. 이전 레벨을 먼저 클리어해주세요."),

    // [5000 ~] : 미구현 기능
    NOT_IMPLEMENTED(HttpStatus.NOT_IMPLEMENTED, 5000, "아직 구현되지 않은 기능입니다."),

    // [9000 ~] : 시스템/서버 에러
    EXTERNAL_API_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, 9000, "외부 API 연동 중 오류가 발생했습니다."),
    DATA_CONVERSION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, 9001, "데이터 변환 중 오류가 발생했습니다."),
    SERVICE_NOT_READY(HttpStatus.SERVICE_UNAVAILABLE, 9002, "데이터 초기화 중입니다. 잠시 후 다시 시도해주세요."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, 9999, "서버 에러가 발생하였습니다. 관리자에게 문의해주세요.");

    private final HttpStatus status;
    private final int code;
    private final String message;

    private ErrorCode(HttpStatus status, int code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

}