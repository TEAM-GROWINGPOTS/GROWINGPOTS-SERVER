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
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final DiscordNotifier discordNotifier;

    // 커스텀 예외
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<BaseResponse<?>> handleBaseException(BaseException e, HttpServletRequest request) {
        log.warn("[BaseException] code={}, message={}", e.getErrorType().getCode(), e.getMessage());
        if (shouldNotify(e.getErrorType().getStatus(), request.getRequestURI())) {
            notify(request, e.getClass().getSimpleName(), e.getMessage());
        }
        return toResponse(e.getErrorType());
    }

    // @Valid 검증 실패 — 필드별 상세 메시지
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BaseResponse<?>> handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
        Map<String, String> errors = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "유효하지 않은 값입니다",
                        (existing, newValue) -> existing + ", " + newValue
                ));
        log.warn("[Validation] {}", errors);
        if (shouldNotify(HttpStatus.BAD_REQUEST, request.getRequestURI())) {
            notify(request, e.getClass().getSimpleName(), errors.toString());
        }
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(BaseResponse.error(ErrorCode.INVALID_INPUT_VALUE));
    }

    // 바인딩 실패
    @ExceptionHandler(BindException.class)
    public ResponseEntity<BaseResponse<?>> handleBindException(BindException e, HttpServletRequest request) {
        log.warn("[BindException] {}", e.getMessage());
        if (shouldNotify(HttpStatus.BAD_REQUEST, request.getRequestURI())) {
            notify(request, e.getClass().getSimpleName(), e.getMessage());
        }
        return toResponse(ErrorCode.INVALID_INPUT_VALUE);
    }

    // 파라미터 타입 불일치
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<BaseResponse<?>> handleTypeMismatch(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        log.warn("[TypeMismatch] param={}", e.getName());
        if (shouldNotify(HttpStatus.BAD_REQUEST, request.getRequestURI())) {
            notify(request, e.getClass().getSimpleName(), "param=" + e.getName());
        }
        return toResponse(ErrorCode.INVALID_FORMAT);
    }

    // 필수 파라미터 누락
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<BaseResponse<?>> handleMissingParam(MissingServletRequestParameterException e, HttpServletRequest request) {
        log.warn("[MissingParam] param={}", e.getParameterName());
        if (shouldNotify(HttpStatus.BAD_REQUEST, request.getRequestURI())) {
            notify(request, e.getClass().getSimpleName(), "param=" + e.getParameterName());
        }
        return toResponse(ErrorCode.MISSING_PARAMETER);
    }

    // 필수 파트(예: multipart 파일) 누락
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<BaseResponse<?>> handleMissingPart(MissingServletRequestPartException e, HttpServletRequest request) {
        log.warn("[MissingPart] part={}", e.getRequestPartName());
        if (shouldNotify(HttpStatus.BAD_REQUEST, request.getRequestURI())) {
            notify(request, e.getClass().getSimpleName(), "part=" + e.getRequestPartName());
        }
        return toResponse(ErrorCode.MISSING_PARAMETER);
    }

    // JSON 파싱 실패
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<BaseResponse<?>> handleNotReadable(HttpMessageNotReadableException e, HttpServletRequest request) {
        String detail;
        if (e.getCause() instanceof InvalidFormatException invalidFormatException) {
            String fieldName = invalidFormatException.getPath().stream()
                    .map(JsonMappingException.Reference::getFieldName)
                    .collect(Collectors.joining("."));
            log.warn("[InvalidFormat] field='{}', value={}", fieldName, invalidFormatException.getValue());
            detail = "field=" + fieldName;
        } else {
            log.warn("[NotReadable] {}", e.getMessage());
            detail = e.getMessage();
        }
        if (shouldNotify(HttpStatus.BAD_REQUEST, request.getRequestURI())) {
            notify(request, e.getClass().getSimpleName(), detail);
        }
        return toResponse(ErrorCode.INVALID_FORMAT);
    }

    // 업로드 용량 초과
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<BaseResponse<?>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e, HttpServletRequest request) {
        log.warn("[MaxUploadSizeExceeded] {}", e.getMessage());
        if (shouldNotify(HttpStatus.PAYLOAD_TOO_LARGE, request.getRequestURI())) {
            notify(request, e.getClass().getSimpleName(), e.getMessage());
        }
        return toResponse(ErrorCode.PDF_TOO_LARGE);
    }

    // 정적 리소스 없음 (Spring 6.1+) — 스캐너 봇이 /login/web.config 같은 경로를 탐색할 때 여기로 옴
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<BaseResponse<?>> handleNoResourceFound(NoResourceFoundException e, HttpServletRequest request) {
        log.warn("[NoResourceFound] {}", request.getRequestURI());
        if (shouldNotify(HttpStatus.NOT_FOUND, request.getRequestURI())) {
            notify(request, e.getClass().getSimpleName(), e.getMessage());
        }
        return toResponse(ErrorCode.RESOURCE_NOT_FOUND);
    }

    // 잘못된 URL
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<BaseResponse<?>> handleNoHandler(NoHandlerFoundException e, HttpServletRequest request) {
        log.warn("[NoHandler] {}", e.getRequestURL());
        if (shouldNotify(HttpStatus.NOT_FOUND, request.getRequestURI())) {
            notify(request, e.getClass().getSimpleName(), e.getRequestURL());
        }
        return toResponse(ErrorCode.RESOURCE_NOT_FOUND);
    }

    // 지원하지 않는 HTTP 메서드
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<BaseResponse<?>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        log.warn("[MethodNotAllowed] {}", e.getMethod());
        if (shouldNotify(HttpStatus.METHOD_NOT_ALLOWED, request.getRequestURI())) {
            notify(request, e.getClass().getSimpleName(), e.getMessage());
        }
        return toResponse(ErrorCode.METHOD_NOT_ALLOWED);
    }

    // IllegalArgumentException
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<BaseResponse<?>> handleIllegalArgument(IllegalArgumentException e, HttpServletRequest request) {
        log.warn("[IllegalArgument] {}", e.getMessage());
        if (shouldNotify(HttpStatus.BAD_REQUEST, request.getRequestURI())) {
            notify(request, e.getClass().getSimpleName(), e.getMessage());
        }
        return toResponse(ErrorCode.INVALID_INPUT_VALUE);
    }

    // 그 외 모든 예외 — 예상치 못한 서버 오류이므로 항상 알림
    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseResponse<?>> handleException(Exception e, HttpServletRequest request) {
        log.error("[UnhandledException] {}", e.getMessage(), e);
        notify(request, e.getClass().getSimpleName(), e.getMessage());
        return toResponse(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    // 5xx → 항상 알림 / 401 → 항상 제외 / 나머지 4xx → /api/** 경로만 알림
    private boolean shouldNotify(HttpStatus status, String requestUri) {
        if (status.is5xxServerError()) return true;
        if (status == HttpStatus.UNAUTHORIZED) return false;
        return status.is4xxClientError() && requestUri.startsWith("/api/");
    }

    private void notify(HttpServletRequest request, String errorClass, String errorMessage) {
        String message = String.format("**URL**: %s %s\n**Error**: %s\n**Message**: %s",
                request.getMethod(), request.getRequestURI(),
                errorClass, errorMessage);
        discordNotifier.sendError("🚨 서버 에러 발생", message);
    }

    private ResponseEntity<BaseResponse<?>> toResponse(ErrorType errorType) {
        return ResponseEntity
                .status(errorType.getStatus())
                .body(BaseResponse.error(errorType));
    }
}