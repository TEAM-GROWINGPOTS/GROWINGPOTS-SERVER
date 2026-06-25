package com.growingpots.global.exception;

import com.growingpots.global.response.error.ErrorType;
import lombok.Getter;

@Getter
public class BaseException extends RuntimeException {

    private final ErrorType errorType;

    // 일반적인 경우
    public BaseException(ErrorType errorType) {
        super(errorType.getMessage());
        this.errorType = errorType;
    }

    // 상세 정보가 필요한 경우 (로그용)
    public BaseException(ErrorType errorType, String detail) {
        super(errorType.getMessage() + " | " + detail);
        this.errorType = errorType;
    }
}