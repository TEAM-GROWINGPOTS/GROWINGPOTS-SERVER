package com.growingpots.domain.university.entity;

import com.growingpots.domain.university.entity.enums.OpenedSemester;
import com.growingpots.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Course extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @Column(nullable = false)
    private String courseCode;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int credit;

    // 여러 학과 공통 개설 교양 과목처럼 특정 학과 소속이 아닌 경우 null
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "offering_department_id")
    private Department offeringDepartment;

    // 이 과목의 일반적 이수구분(예정 과목 시뮬레이션·플래너 미리보기용). 실제 인정 영역은 STUDENT_COURSE.appliedDivision 우선
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_division_id")
    private Division defaultDivision;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ge_area_id")
    private GeArea geArea;

    // 원래 "1-2", "3-4" 같은 범위 문자열이었으나, 과목 검색 필터(학년)에서 매 요청마다 문자열을
    // 파싱하지 않도록 등록 시점에 한 번만 파싱해 정수 범위로 저장한다. 단일 학년이면 low==high.
    private Integer recommendedYearLow;
    private Integer recommendedYearHigh;

    @Enumerated(EnumType.STRING)
    private OpenedSemester openedSemester;

    @Column(nullable = false)
    private boolean isEnglish;

    @Column(nullable = false)
    private boolean isSw;

    // 현재 교육과정(검색 가능)인지 여부. 폐지/개정된 옛날 과목은 false로 남겨둬서 과거 학번 학생의
    // 이수 과목 매칭(학과명/권장학년 등 표시)엔 계속 쓰이되, 과목 검색(플래너-과목 추가)엔 노출되지
    // 않는다. 기존 row가 있는 테이블에 컬럼을 추가하는 거라 DB 기본값도 true로 맞춰 마이그레이션이
    // 안전하게 되도록 한다.
    @Column(nullable = false, columnDefinition = "TINYINT(1) DEFAULT 1")
    private boolean isActive;

    @Builder
    private Course(
            School school,
            String courseCode,
            String name,
            int credit,
            Department offeringDepartment,
            Division defaultDivision,
            GeArea geArea,
            Integer recommendedYearLow,
            Integer recommendedYearHigh,
            OpenedSemester openedSemester,
            boolean isEnglish,
            boolean isSw,
            boolean isActive
    ) {
        this.school = school;
        this.courseCode = courseCode;
        this.name = name;
        this.credit = credit;
        this.offeringDepartment = offeringDepartment;
        this.defaultDivision = defaultDivision;
        this.geArea = geArea;
        this.recommendedYearLow = recommendedYearLow;
        this.recommendedYearHigh = recommendedYearHigh;
        this.openedSemester = openedSemester;
        this.isEnglish = isEnglish;
        this.isSw = isSw;
        this.isActive = isActive;
    }
}