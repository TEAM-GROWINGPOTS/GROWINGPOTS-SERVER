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

    @Builder
    private Course(School school, String courseCode, String name, int credit, Department offeringDepartment) {
        this.school = school;
        this.courseCode = courseCode;
        this.name = name;
        this.credit = credit;
        this.offeringDepartment = offeringDepartment;
    }
}
