package com.growingpots.domain.transcript.entity;

import com.growingpots.domain.user.entity.StudentMajor;
import com.growingpots.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// PDF 상단 졸업요건 분석 결과를 과목 목록에서 재계산하지 않고 그대로 스냅샷 저장한다.
// 교양(필수교과/배분이수/자유이수)은 전공과 무관한 학생 전체 공통값이라, 복수전공자는 전공별 행마다 동일한 값이 중복 저장된다.
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GraduationAnalysisSummary extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "student_major_id", nullable = false, unique = true)
    private StudentMajor studentMajor;

    private int totalCreditCurrent;
    private int totalCreditRequired;

    @Column(precision = 5, scale = 3)
    private BigDecimal gpaCurrent;
    @Column(precision = 5, scale = 3)
    private BigDecimal gpaRequired;

    private int englishCurrent;
    private int englishRequired;

    // PDF 상단 요약(기준/취득)의 마지막 컬럼이 SW인증 학점. 통과/미통과 자체는 CERT_RESULT.SW가 별도로 관리
    private Integer swCertCurrent;
    private Integer swCertRequired;

    private int majorBasicCurrent;
    private int majorBasicRequired;

    private int majorRequiredCurrent;
    private int majorRequiredRequired;

    private int majorElectiveCurrent;
    private int majorElectiveRequired;

    private int requiredPlusElectiveCurrent;
    private int requiredPlusElectiveRequired;

    @Column(name = "required_ge_current")
    private int requiredGeCurrent;
    @Column(name = "required_ge_required")
    private int requiredGeRequired;

    @Column(name = "distributed_ge_current")
    private int distributedGeCurrent;
    @Column(name = "distributed_ge_required")
    private int distributedGeRequired;

    @Column(name = "free_ge_current")
    private int freeGeCurrent;
    @Column(name = "free_ge_required")
    private int freeGeRequired;

    // "기타/공통/일반선택" 이수영역은 PDF에 기준(요구) 학점이 없어 취득 학점만 저장한다.
    private int generalElectiveCurrent;

    @Builder
    private GraduationAnalysisSummary(
            StudentMajor studentMajor,
            int totalCreditCurrent,
            int totalCreditRequired,
            BigDecimal gpaCurrent,
            BigDecimal gpaRequired,
            int englishCurrent,
            int englishRequired,
            Integer swCertCurrent,
            Integer swCertRequired,
            int majorBasicCurrent,
            int majorBasicRequired,
            int majorRequiredCurrent,
            int majorRequiredRequired,
            int majorElectiveCurrent,
            int majorElectiveRequired,
            int requiredPlusElectiveCurrent,
            int requiredPlusElectiveRequired,
            int requiredGeCurrent,
            int requiredGeRequired,
            int distributedGeCurrent,
            int distributedGeRequired,
            int freeGeCurrent,
            int freeGeRequired,
            int generalElectiveCurrent
    ) {
        this.studentMajor = studentMajor;
        this.totalCreditCurrent = totalCreditCurrent;
        this.totalCreditRequired = totalCreditRequired;
        this.gpaCurrent = gpaCurrent;
        this.gpaRequired = gpaRequired;
        this.englishCurrent = englishCurrent;
        this.englishRequired = englishRequired;
        this.swCertCurrent = swCertCurrent;
        this.swCertRequired = swCertRequired;
        this.majorBasicCurrent = majorBasicCurrent;
        this.majorBasicRequired = majorBasicRequired;
        this.majorRequiredCurrent = majorRequiredCurrent;
        this.majorRequiredRequired = majorRequiredRequired;
        this.majorElectiveCurrent = majorElectiveCurrent;
        this.majorElectiveRequired = majorElectiveRequired;
        this.requiredPlusElectiveCurrent = requiredPlusElectiveCurrent;
        this.requiredPlusElectiveRequired = requiredPlusElectiveRequired;
        this.requiredGeCurrent = requiredGeCurrent;
        this.requiredGeRequired = requiredGeRequired;
        this.distributedGeCurrent = distributedGeCurrent;
        this.distributedGeRequired = distributedGeRequired;
        this.freeGeCurrent = freeGeCurrent;
        this.freeGeRequired = freeGeRequired;
        this.generalElectiveCurrent = generalElectiveCurrent;
    }

    // 재업로드 시 기존 스냅샷 행을 새 파싱 결과로 덮어쓴다 (studentMajor는 유지)
    public void updateFrom(GraduationAnalysisSummary newValues) {
        this.totalCreditCurrent = newValues.totalCreditCurrent;
        this.totalCreditRequired = newValues.totalCreditRequired;
        this.gpaCurrent = newValues.gpaCurrent;
        this.gpaRequired = newValues.gpaRequired;
        this.englishCurrent = newValues.englishCurrent;
        this.englishRequired = newValues.englishRequired;
        this.swCertCurrent = newValues.swCertCurrent;
        this.swCertRequired = newValues.swCertRequired;
        this.majorBasicCurrent = newValues.majorBasicCurrent;
        this.majorBasicRequired = newValues.majorBasicRequired;
        this.majorRequiredCurrent = newValues.majorRequiredCurrent;
        this.majorRequiredRequired = newValues.majorRequiredRequired;
        this.majorElectiveCurrent = newValues.majorElectiveCurrent;
        this.majorElectiveRequired = newValues.majorElectiveRequired;
        this.requiredPlusElectiveCurrent = newValues.requiredPlusElectiveCurrent;
        this.requiredPlusElectiveRequired = newValues.requiredPlusElectiveRequired;
        this.requiredGeCurrent = newValues.requiredGeCurrent;
        this.requiredGeRequired = newValues.requiredGeRequired;
        this.distributedGeCurrent = newValues.distributedGeCurrent;
        this.distributedGeRequired = newValues.distributedGeRequired;
        this.freeGeCurrent = newValues.freeGeCurrent;
        this.freeGeRequired = newValues.freeGeRequired;
        this.generalElectiveCurrent = newValues.generalElectiveCurrent;
    }
}
