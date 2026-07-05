package com.growingpots.domain.transcript.entity;

import com.growingpots.domain.transcript.entity.enums.CourseStatus;
import com.growingpots.domain.transcript.entity.enums.RecordSource;
import com.growingpots.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    private String rawCourseCode;

    @Column(nullable = false)
    private String rawCourseName;

    @Column(nullable = false)
    private int credit;

    // 금학기수강학점(진행 중) 과목은 PDF에 수강년도/학기가 표기되지 않아 null (status=IN_PROGRESS)
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
            Long memberId,
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
        this.memberId = memberId;
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
