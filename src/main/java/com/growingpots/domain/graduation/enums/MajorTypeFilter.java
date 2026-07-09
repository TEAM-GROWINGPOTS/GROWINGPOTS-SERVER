package com.growingpots.domain.graduation.enums;

// PRIMARY/MULTI는 폐지됐다 - 복수전공을 여러 개 가진 학생도 있어 "본전공/복수전공" 이분법으로는
// 표현이 안 된다. 특정 전공 하나만 조회할 땐 이 enum 대신 department(학과명) 파라미터를 쓴다.
public enum MajorTypeFilter {
    ALL, GE, OTHERS
}