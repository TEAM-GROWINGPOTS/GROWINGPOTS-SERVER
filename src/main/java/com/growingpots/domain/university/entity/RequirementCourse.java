package com.growingpots.domain.university.entity;

import com.growingpots.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
public class RequirementCourse extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    // null이면 이수구분(전공기초/필수/선택 등)과 무관한, 학과 자체의 독립적인 졸업요건이다
    // (예: 스포츠의학과 졸업필수 - 전문실기/맨손체조). 이 경우 division 기반 조회에는 안 걸리고,
    // GraduationConditionType.GRADUATION_REQUIRED 조회에서 department로만 찾는다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "division_id")
    private Division division;

    // 이 요건이 적용되기 시작하는 입학년도. baseYear <= 학생 입학년도 조건으로 조회.
    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int baseYear;

    private int minCount;
    private int minCredit;

    @Builder
    private RequirementCourse(Department department, Division division, String name, int baseYear, int minCount, int minCredit) {
        this.department = department;
        this.division = division;
        this.name = name;
        this.baseYear = baseYear;
        this.minCount = minCount;
        this.minCredit = minCredit;
    }
}