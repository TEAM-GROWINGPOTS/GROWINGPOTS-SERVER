package com.growingpots.global.response.success;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SuccessCode implements SuccessType {

    // Common
    OK(HttpStatus.OK, "CMN_200", "요청에 성공했습니다."),
    CREATED(HttpStatus.CREATED, "CMN_201", "리소스가 생성됐습니다."),
    NO_CONTENT(HttpStatus.NO_CONTENT, "CMN_204", "요청에 성공했습니다."),

    // Auth
    LOGIN_SUCCESS(HttpStatus.OK, "AUTH_200", "로그인에 성공했습니다."),
    LOGOUT_SUCCESS(HttpStatus.OK, "AUTH_200_1", "로그아웃에 성공했습니다."),
    TOKEN_REISSUED(HttpStatus.OK, "AUTH_200_2", "토큰이 재발급됐습니다."),

    // User
    USER_FOUND(HttpStatus.OK, "USER_200", "사용자 조회에 성공했습니다."),
    USER_UPDATED(HttpStatus.OK, "USER_200_1", "사용자 정보가 수정됐습니다."),

    // University
    UNIVERSITY_LIST_FOUND(HttpStatus.OK, "UNIV_200", "학교 목록 조회에 성공했습니다."),
    MAJOR_LIST_FOUND(HttpStatus.OK, "UNIV_200_1", "학과 목록 조회에 성공했습니다."),

    // Transcript
    PDF_PARSED(HttpStatus.CREATED, "TRANS_201", "PDF 파싱에 성공했습니다."),

    // Requirement
    REQUIREMENT_FOUND(HttpStatus.OK, "REQ_200", "졸업요건 조회에 성공했습니다."),

    // Planner
    PLANNER_FOUND(HttpStatus.OK, "PLAN_200", "플래너 조회에 성공했습니다."),
    PLANNER_COURSE_ADDED(HttpStatus.CREATED, "PLAN_201", "플래너에 과목이 추가됐습니다."),
    PLANNER_COURSE_DELETED(HttpStatus.NO_CONTENT, "PLAN_204", "플래너에서 과목이 삭제됐습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}