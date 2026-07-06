package com.growingpots.domain.transcript.entity;

import com.growingpots.domain.transcript.entity.enums.CertJudgement;
import com.growingpots.domain.transcript.entity.enums.CertType;
import com.growingpots.domain.transcript.entity.enums.RecordSource;
import com.growingpots.domain.user.entity.StudentMajor;
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
public class CertResult extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_profile_id", nullable = false)
    private StudentProfile studentProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_major_id", nullable = false)
    private StudentMajor studentMajor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CertType certType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CertJudgement result;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecordSource source;

    @Builder
    private CertResult(StudentProfile studentProfile, StudentMajor studentMajor, CertType certType, CertJudgement result, RecordSource source) {
        this.studentProfile = studentProfile;
        this.studentMajor = studentMajor;
        this.certType = certType;
        this.result = result;
        this.source = source;
    }
}
