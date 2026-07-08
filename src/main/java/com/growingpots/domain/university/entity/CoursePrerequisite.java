package com.growingpots.domain.university.entity;

import com.growingpots.domain.university.entity.enums.PrerequisiteType;
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
public class CoursePrerequisite extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "required_course_id", nullable = false)
    private Course requiredCourse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PrerequisiteType prerequisiteType;

    // null이면 모든 학과 공통 적용, 지정되면 해당 학과 학생에게만 적용
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Builder
    private CoursePrerequisite(
            Course course,
            Course requiredCourse,
            PrerequisiteType prerequisiteType,
            Department department
    ) {
        this.course = course;
        this.requiredCourse = requiredCourse;
        this.prerequisiteType = prerequisiteType;
        this.department = department;
    }
}
