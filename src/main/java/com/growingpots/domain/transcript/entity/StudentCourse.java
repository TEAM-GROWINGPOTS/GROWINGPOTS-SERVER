package com.growingpots.domain.transcript.entity;

import com.growingpots.domain.transcript.entity.enums.CourseStatus;
import com.growingpots.domain.transcript.entity.enums.RecordSource;
import com.growingpots.domain.university.entity.Course;
import com.growingpots.domain.university.entity.Division;
import com.growingpots.domain.user.entity.StudentProfile;
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
public class StudentCourse extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_profile_id", nullable = false)
    private StudentProfile studentProfile;

    // COURSE 마스터와 매칭 실패 시(시드 데이터 없음/학수번호 불일치) null
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    // TranscriptPersister가 rawClassification/section → Division 조회 후 채운다. 미매칭 시 null.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applied_division_id")
    private Division appliedDivision;

    private String rawCourseCode;

    @Column(nullable = false)
    private String rawCourseName;

    @Column(nullable = false)
    private int credit;

    // 금학기수강학점(진행 중) 과목은 PDF에 수강년도/학기가 없어 오늘 날짜 기준 학사년도/학기로 채워진다 (status=IN_PROGRESS)
    private Integer takenYear;

    private String takenSemester;

    @Column(nullable = false)
    private String section;

    private String rawClassification;

    @Column(nullable = false)
    private boolean isRetake;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CourseStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecordSource source;

    @Builder
    private StudentCourse(
            StudentProfile studentProfile,
            Course course,
            Division appliedDivision,
            String rawCourseCode,
            String rawCourseName,
            int credit,
            Integer takenYear,
            String takenSemester,
            String section,
            String rawClassification,
            boolean isRetake,
            CourseStatus status,
            RecordSource source
    ) {
        this.studentProfile = studentProfile;
        this.course = course;
        this.appliedDivision = appliedDivision;
        this.rawCourseCode = rawCourseCode;
        this.rawCourseName = rawCourseName;
        this.credit = credit;
        this.takenYear = takenYear;
        this.takenSemester = takenSemester;
        this.section = section;
        this.rawClassification = rawClassification;
        this.isRetake = isRetake;
        this.status = status;
        this.source = source;
    }
}
