package com.growingpots.domain.planner.entity;

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
public class PlannerTerm extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "planner_simulation_id", nullable = false)
    private PlannerSimulation plannerSimulation;

    @Column(nullable = false)
    private int yearLevel;

    // 1=1학기, 2=2학기
    @Column(nullable = false)
    private int semester;

    @Column(nullable = false)
    private int termOrder;

    @Builder
    private PlannerTerm(PlannerSimulation plannerSimulation, int yearLevel, int semester, int termOrder) {
        this.plannerSimulation = plannerSimulation;
        this.yearLevel = yearLevel;
        this.semester = semester;
        this.termOrder = termOrder;
    }
}