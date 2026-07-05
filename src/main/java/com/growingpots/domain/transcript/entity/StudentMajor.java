package com.growingpots.domain.transcript.entity;

import com.growingpots.domain.transcript.entity.enums.MajorType;
import com.growingpots.domain.university.entity.Department;
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
public class StudentMajor extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MajorType majorType;

    // PDF에서 파싱한 전공명이 DEPARTMENT 마스터와 매칭되지 않으면 null (신규 학과/이름 표기 차이 등)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Builder
    private StudentMajor(Long memberId, MajorType majorType, Department department) {
        this.memberId = memberId;
        this.majorType = majorType;
        this.department = department;
    }
}
