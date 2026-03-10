package com.bremenband.shadowengapi.global.exception;

import lombok.Getter;

import java.io.Serial;

@Getter
public class CustomException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 5488071593387696121L;

    private final ErrorCode errorCode;

    public CustomException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

}
