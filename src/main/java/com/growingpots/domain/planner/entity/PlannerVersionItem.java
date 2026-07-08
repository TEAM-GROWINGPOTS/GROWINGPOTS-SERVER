package com.growingpots.domain.planner.entity;

import com.growingpots.domain.university.entity.Course;
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

    @Column(nullable = false)
    private int credit;

    @Column(nullable = false)
    private int positionOrder;

    @Builder
    private PlannerVersionItem(
            PlannerTermVersion plannerTermVersion,
            Course course,
            int credit,
            int positionOrder
    ) {
        this.plannerTermVersion = plannerTermVersion;
        this.course = course;
        this.credit = credit;
        this.positionOrder = positionOrder;
    }
}