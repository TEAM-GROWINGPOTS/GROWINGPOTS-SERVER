package com.growingpots.domain.user.entity;

import com.growingpots.domain.university.entity.Department;
import com.growingpots.domain.university.entity.School;
import com.growingpots.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentProfile extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false, unique = true)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(nullable = false)
    private int admissionYear;

    // 아래 4개는 온보딩 시점엔 비어있다가 PDF 분석 시점에 채워진다.
    private String studentNo;

    private String enrollmentStatus;

    private Integer currentGrade;

    private Integer currentTerm;

    @Builder
    private StudentProfile(Member member, School school, Department department, int admissionYear) {
        this.member = member;
        this.school = school;
        this.department = department;
        this.admissionYear = admissionYear;
    }

    // 온보딩 기본정보 화면에서 뒤로 가서 다시 제출한 경우 덮어쓴다(#221). PDF를 아직 분석 안 한
    // 상태(GraduationAnalysisSummary 없음)에서만 호출되므로 학과가 바뀌어도 붕 뜨는 이수 과목 데이터가
    // 없다 - 이 시점엔 StudentCourse가 아예 없다.
    public void updateOnboardingInfo(School school, Department department, int admissionYear) {
        this.school = school;
        this.department = department;
        this.admissionYear = admissionYear;
    }

    // PDF 파싱 결과(학번/재학상태/학년)로 학적 정보를 갱신한다.
    // admissionYear는 학번 앞자리에서 뽑은 값이 있을 때만 덮어쓴다(온보딩 때 입력한 값보다 학번 기준이 더 신뢰할 수 있음).
    public void updateAcademicInfo(
            String studentNo, String enrollmentStatus, Integer currentGrade, Integer admissionYearFromStudentNo, int currentTerm) {
        this.studentNo = studentNo;
        this.enrollmentStatus = enrollmentStatus;
        this.currentGrade = currentGrade;
        if (admissionYearFromStudentNo != null) {
            this.admissionYear = admissionYearFromStudentNo;
        }
        this.currentTerm = currentTerm;
    }
}