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
public class PlannerTermVersion extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "planner_term_id", nullable = false)
    private PlannerTerm plannerTerm;

    @Column(nullable = false)
    private int versionNo;

    private String name;

    @Column(nullable = false)
    private boolean isSelected;

    @Builder
    private PlannerTermVersion(PlannerTerm plannerTerm, int versionNo, String name, boolean isSelected) {
        this.plannerTerm = plannerTerm;
        this.versionNo = versionNo;
        this.name = name;
        this.isSelected = isSelected;
    }
}