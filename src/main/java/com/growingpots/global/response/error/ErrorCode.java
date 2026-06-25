package com.growingpots.global.response.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode implements ErrorType {

    // Common
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "CMN_001", "서버 오류가 발생했습니다."),
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "CMN_002", "잘못된 입력값입니다."),
    INVALID_FORMAT(HttpStatus.BAD_REQUEST, "CMN_003", "요청 데이터 형식이 올바르지 않습니다."),
    MISSING_PARAMETER(HttpStatus.BAD_REQUEST, "CMN_004", "필수 파라미터가 누락되었습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "CMN_005", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "CMN_006", "접근 권한이 없습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "CMN_007", "리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "CMN_008", "허용되지 않은 HTTP 메서드입니다."),

    // Auth
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_001", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_002", "만료된 토큰입니다."),
    INVALID_OAUTH_PROVIDER(HttpStatus.BAD_REQUEST, "AUTH_003", "지원하지 않는 소셜 로그인입니다."),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_001", "존재하지 않는 사용자입니다."),

    // University
    UNIVERSITY_NOT_FOUND(HttpStatus.NOT_FOUND, "UNIV_001", "존재하지 않는 학교입니다."),
    MAJOR_NOT_FOUND(HttpStatus.NOT_FOUND, "UNIV_002", "존재하지 않는 학과입니다."),

    // Transcript
    PDF_PARSING_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "TRANS_001", "PDF 파싱에 실패했습니다."),
    PDF_INVALID_FORMAT(HttpStatus.BAD_REQUEST, "TRANS_002", "지원하지 않는 PDF 형식입니다."),
    LLM_API_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "TRANS_003", "AI 파싱 중 오류가 발생했습니다."),

    // Requirement
    REQUIREMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "REQ_001", "졸업요건 데이터가 존재하지 않습니다."),

    // Planner
    COURSE_NOT_FOUND(HttpStatus.NOT_FOUND, "PLAN_001", "존재하지 않는 과목입니다."),
    PLANNER_COURSE_NOT_FOUND(HttpStatus.NOT_FOUND, "PLAN_002", "플래너에 존재하지 않는 과목입니다."),
    DUPLICATE_PLANNER_COURSE(HttpStatus.BAD_REQUEST, "PLAN_003", "이미 플래너에 추가된 과목입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}