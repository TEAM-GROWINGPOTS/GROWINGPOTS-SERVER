package com.growingpots.domain.university.entity.enums;

// 과목 검색 API의 이수영역 필터 전용 값. 앞 7개는 DivisionCategory와 이름이 그대로 대응된다.
// CROSS_MAJOR(타전공인정과목)는 DIVISION.category 값이 아니라 CrossMajorRecognizedCourse로 따로 조회한다.
public enum CourseDivisionFilter {
    MAJOR_BASIC,
    MAJOR_REQUIRED,
    MAJOR_ELECTIVE,
    REQUIRED_GE,
    DISTRIBUTED_GE,
    FREE_GE,
    GENERAL_ELECTIVE,
    CROSS_MAJOR
}
