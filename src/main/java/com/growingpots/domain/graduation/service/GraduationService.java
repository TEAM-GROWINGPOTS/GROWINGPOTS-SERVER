package com.growingpots.domain.graduation.service;

import com.growingpots.domain.graduation.dto.response.GraduationResponse;
import com.growingpots.domain.graduation.dto.response.GraduationResponse.CertInfo;
import com.growingpots.domain.graduation.dto.response.GraduationResponse.ConditionInfo;
import com.growingpots.domain.graduation.dto.response.GraduationResponse.CreditInfo;
import com.growingpots.domain.graduation.dto.response.GraduationResponse.GpaInfo;
import com.growingpots.domain.graduation.dto.response.GraduationResponse.Summary;
import com.growingpots.domain.graduation.enums.GraduationConditionType;
import com.growingpots.domain.graduation.enums.MajorTypeFilter;
import com.growingpots.domain.transcript.entity.CertResult;
import com.growingpots.domain.transcript.entity.GraduationAnalysisSummary;
import com.growingpots.domain.transcript.repository.CertResultRepository;
import com.growingpots.domain.transcript.repository.GraduationAnalysisSummaryRepository;
import com.growingpots.domain.user.entity.StudentMajor;
import com.growingpots.domain.user.entity.StudentMajor.MajorType;
import com.growingpots.domain.user.entity.StudentProfile;
import com.growingpots.domain.user.repository.StudentMajorRepository;
import com.growingpots.domain.user.repository.StudentProfileRepository;
import com.growingpots.global.exception.BaseException;
import com.growingpots.global.response.error.ErrorCode;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GraduationService {

    private final StudentProfileRepository studentProfileRepository;
    private final StudentMajorRepository studentMajorRepository;
    private final GraduationAnalysisSummaryRepository graduationAnalysisSummaryRepository;
    private final CertResultRepository certResultRepository;

    @Transactional(readOnly = true)
    public GraduationResponse getGraduation(Long memberId, MajorTypeFilter majorTypeFilter) {
        StudentProfile profile = studentProfileRepository.findWithDetailsByMemberId(memberId)
                .orElseThrow(() -> new BaseException(ErrorCode.STUDENT_PROFILE_NOT_FOUND));

        List<StudentMajor> majors = studentMajorRepository.findWithDepartmentByStudentProfile(profile);

        StudentMajor mainMajor = majors.stream()
                .filter(m -> m.getMajorType() == MajorType.MAIN)
                .findFirst()
                .orElseThrow(() -> new BaseException(ErrorCode.STUDENT_PROFILE_NOT_FOUND));

        // TODO(planner-source): source=PLANNED 요청 시 플래너 데이터 기반 계산 구현
        return switch (majorTypeFilter) {
            case PRIMARY -> {
                GraduationAnalysisSummary summary = requireSummary(mainMajor);
                List<CertResult> certs = certResultRepository.findByStudentMajor(mainMajor);
                yield buildResponse(profile, summary, null, certs);
            }
            case MULTI -> {
                StudentMajor doubleMajor = majors.stream()
                        .filter(m -> m.getMajorType() == MajorType.DOUBLE)
                        .findFirst()
                        .orElseThrow(() -> new BaseException(ErrorCode.REQUIREMENT_NOT_FOUND));
                GraduationAnalysisSummary summary = requireSummary(doubleMajor);
                List<CertResult> certs = certResultRepository.findByStudentMajor(doubleMajor);
                yield buildResponse(profile, summary, null, certs);
            }
            case ALL -> {
                GraduationAnalysisSummary mainSummary = requireSummary(mainMajor);
                GraduationAnalysisSummary doubleSummary = majors.stream()
                        .filter(m -> m.getMajorType() == MajorType.DOUBLE)
                        .findFirst()
                        .flatMap(graduationAnalysisSummaryRepository::findByStudentMajor)
                        .orElse(null);
                List<CertResult> certs = certResultRepository.findByStudentMajor(mainMajor);
                yield buildResponse(profile, mainSummary, doubleSummary, certs);
            }
        };
    }

    private GraduationAnalysisSummary requireSummary(StudentMajor studentMajor) {
        return graduationAnalysisSummaryRepository.findByStudentMajor(studentMajor)
                .orElseThrow(() -> new BaseException(ErrorCode.REQUIREMENT_NOT_FOUND));
    }

    private GraduationResponse buildResponse(
            StudentProfile profile,
            GraduationAnalysisSummary baseSummary,
            GraduationAnalysisSummary doubleSummary,
            List<CertResult> certs
    ) {
        Summary summary = Summary.builder()
                .totalCredits(new CreditInfo(baseSummary.getTotalCreditCurrent(), baseSummary.getTotalCreditRequired()))
                .gpa(new GpaInfo(baseSummary.getGpaCurrent(), baseSummary.getGpaRequired()))
                .enrollmentStatus(profile.getEnrollmentStatus())
                .build();

        List<ConditionInfo> conditions = Arrays.stream(GraduationConditionType.values())
                .map(type -> toConditionInfo(type, baseSummary, doubleSummary))
                .toList();

        List<CertInfo> certInfos = certs.stream()
                .map(c -> new CertInfo(c.getCertType().name(), c.getResult().name()))
                .toList();

        return GraduationResponse.builder()
                .summary(summary)
                .conditions(conditions)
                .certs(certInfos)
                .build();
    }

    private ConditionInfo toConditionInfo(
            GraduationConditionType type,
            GraduationAnalysisSummary base,
            GraduationAnalysisSummary extra
    ) {
        int current = type.getCurrentExtractor().applyAsInt(base);
        Integer required = type.getRequiredExtractor() != null
                ? type.getRequiredExtractor().apply(base)
                : null;

        // aggregatable=true인 항목만 복수전공(DOUBLE) 스냅샷 합산 (MAJOR_BASIC/REQUIRED/ELECTIVE)
        if (extra != null && type.isAggregatable()) {
            current += type.getCurrentExtractor().applyAsInt(extra);
            if (required != null) {
                required += type.getRequiredExtractor().apply(extra);
            }
        }

        // required=null이면 기준 없음 → 미달 불가 (GENERAL_ELECTIVE)
        boolean satisfied = required == null || current >= required;

        return ConditionInfo.builder()
                .code(type.name())
                .name(type.getDisplayName())
                .current(current)
                .required(required)
                .unit(type.getUnit())
                .satisfied(satisfied)
                .chartTarget(type.isChartTarget())
                .build();
    }
}