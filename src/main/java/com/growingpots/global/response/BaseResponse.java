package com.growingpots.global.response;

import com.growingpots.global.response.error.ErrorType;
import com.growingpots.global.response.success.SuccessType;

public record BaseResponse<T>(
        boolean success,
        String code,
        String message,
        T data
) {
    // 데이터 있는 성공
    public static <T> BaseResponse<T> success(SuccessType successType, T data) {
        return new BaseResponse<>(true, successType.getCode(), successType.getMessage(), data);
    }

    // 데이터 없는 성공
    public static <T> BaseResponse<T> success(SuccessType successType) {
        return new BaseResponse<>(true, successType.getCode(), successType.getMessage(), null);
    }

    // 실패
    public static <T> BaseResponse<T> error(ErrorType errorType) {
        return new BaseResponse<>(false, errorType.getCode(), errorType.getMessage(), null);
    }
}