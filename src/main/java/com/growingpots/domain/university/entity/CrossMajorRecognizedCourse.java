package com.growingpots.domain.university.entity;

import com.growingpots.global.entity.BaseTimeEntity;
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

// 타전공 인정 과목. "누구 학과 기준으로 보느냐"에 따라 인정 여부/이수구분이 달라지는 건
// 과목 자체의 고정 속성으로 표현할 수 없어(COURSE.default_division_id와 별개로) 학과 간 관계로 저장한다.
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CrossMajorRecognizedCourse extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 이 과목을 타전공으로 인정해주는 학과 (COURSE.offering_department와 다른 게 보통)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_department_id", nullable = false)
    private Department targetDepartment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    // targetDepartment 기준으로 이 과목이 인정되는 이수구분 (course.defaultDivision과 다를 수 있음)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recognized_division_id", nullable = false)
    private Division recognizedDivision;

    @Builder
    private CrossMajorRecognizedCourse(Department targetDepartment, Course course, Division recognizedDivision) {
        this.targetDepartment = targetDepartment;
        this.course = course;
        this.recognizedDivision = recognizedDivision;
    }
}
