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
public class Division extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    // PDF/시스템 내부 코드 (예: "04", "필수교과"). TranscriptPersister에서 Division 매칭에 사용.
    @Column(nullable = false)
    private String code;

    // GraduationConditionType.name()과 매칭되는 값 (예: "MAJOR_REQUIRED"). API divisionCode로 조회.
    @Column(nullable = false)
    private String category;

    @Builder
    private Division(School school, String code, String category) {
        this.school = school;
        this.code = code;
        this.category = category;
    }
}