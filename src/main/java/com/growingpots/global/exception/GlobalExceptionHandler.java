package com.growingpots.global.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.growingpots.global.discord.DiscordNotifier;
import com.growingpots.global.response.BaseResponse;
import com.growingpots.global.response.error.ErrorCode;
import com.growingpots.global.response.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final DiscordNotifier discordNotifier;

    // 커스텀 예외
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<BaseResponse<?>> handleBaseException(BaseException e) {
        log.warn("[BaseException] code={}, message={}",
                e.getErrorType().getCode(), e.getMessage());
        return toResponse(e.getErrorType());
    }

    // @Valid 검증 실패 — 필드별 상세 메시지
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BaseResponse<?>> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> errors = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "유효하지 않은 값입니다",
                        (existing, newValue) -> existing + ", " + newValue
                ));
        log.warn("[Validation] {}", errors);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(BaseResponse.error(ErrorCode.INVALID_INPUT_VALUE));
    }

    // 바인딩 실패
    @ExceptionHandler(BindException.class)
    public ResponseEntity<BaseResponse<?>> handleBindException(BindException e) {
        log.warn("[BindException] {}", e.getMessage());
        return toResponse(ErrorCode.INVALID_INPUT_VALUE);
    }

    // 파라미터 타입 불일치
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<BaseResponse<?>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("[TypeMismatch] param={}", e.getName());
        return toResponse(ErrorCode.INVALID_FORMAT);
    }

    // 필수 파라미터 누락
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<BaseResponse<?>> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("[MissingParam] param={}", e.getParameterName());
        return toResponse(ErrorCode.MISSING_PARAMETER);
    }

    // JSON 파싱 실패
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<BaseResponse<?>> handleNotReadable(HttpMessageNotReadableException e) {
        if (e.getCause() instanceof InvalidFormatException invalidFormatException) {
            String fieldName = invalidFormatException.getPath().stream()
                    .map(JsonMappingException.Reference::getFieldName)
                    .collect(Collectors.joining("."));
            log.warn("[InvalidFormat] field='{}', value={}",
                    fieldName, invalidFormatException.getValue());
        } else {
            log.warn("[NotReadable] {}", e.getMessage());
        }
        return toResponse(ErrorCode.INVALID_FORMAT);
    }

    // 잘못된 URL
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<BaseResponse<?>> handleNoHandler(NoHandlerFoundException e) {
        log.warn("[NoHandler] {}", e.getRequestURL());
        return toResponse(ErrorCode.RESOURCE_NOT_FOUND);
    }

    // 지원하지 않는 HTTP 메서드
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<BaseResponse<?>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        log.warn("[MethodNotAllowed] {}", e.getMethod());
        return toResponse(ErrorCode.METHOD_NOT_ALLOWED);
    }

    // IllegalArgumentException
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<BaseResponse<?>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("[IllegalArgument] {}", e.getMessage());
        return toResponse(ErrorCode.INVALID_INPUT_VALUE);
    }

    // 그 외 모든 예외
    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseResponse<?>> handleException(Exception e, HttpServletRequest request) {
        log.error("[UnhandledException] {}", e.getMessage(), e);
        String message = String.format("**URL**: %s %s\n**Error**: %s\n**Message**: %s",
                request.getMethod(), request.getRequestURI(),
                e.getClass().getSimpleName(), e.getMessage());
        discordNotifier.sendError("🚨 서버 에러 발생", message);
        return toResponse(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    // 공통 응답 변환
    private ResponseEntity<BaseResponse<?>> toResponse(ErrorType errorType) {
        return ResponseEntity
                .status(errorType.getStatus())
                .body(BaseResponse.error(errorType));
    }
}