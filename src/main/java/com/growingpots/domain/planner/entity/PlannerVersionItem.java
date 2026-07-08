package com.growingpots.domain.planner.entity;

import com.growingpots.domain.university.entity.Course;
import com.growingpots.domain.university.entity.Division;
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
public class PlannerVersionItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "planner_term_version_id", nullable = false)
    private PlannerTermVersion plannerTermVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    // 이 학생이 이 과목을 추가할 때 인정받는 이수구분. 타전공인정과목이면 course.defaultDivision과
    // 다를 수 있어 저장 시점에 확정해둔다(과목 자체에 고정된 값이 아니라 학생 학과 기준으로 달라짐).
    // course.defaultDivision이 없는 과목도 있어 nullable.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "planned_division_id")
    private Division plannedDivision;

    @Column(nullable = false)
    private int credit;

    @Column(nullable = false)
    private int positionOrder;

    @Builder
    private PlannerVersionItem(
            PlannerTermVersion plannerTermVersion,
            Course course,
            Division plannedDivision,
            int credit,
            int positionOrder
    ) {
        this.plannerTermVersion = plannerTermVersion;
        this.course = course;
        this.plannedDivision = plannedDivision;
        this.credit = credit;
        this.positionOrder = positionOrder;
    }
}