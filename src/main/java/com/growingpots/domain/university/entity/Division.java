package com.growingpots.domain.university.entity;

import com.growingpots.domain.university.entity.enums.DivisionCategory;
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

// 이수구분 마스터. 판정/집계 로직은 code(학교별 코드)가 아니라 category로 분기한다.
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Division extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @Column(nullable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DivisionCategory category;

    @Builder
    private Division(School school, String code, DivisionCategory category) {
        this.school = school;
        this.code = code;
        this.category = category;
    }
}
