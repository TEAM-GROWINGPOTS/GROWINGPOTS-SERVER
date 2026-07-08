package com.growingpots.domain.graduation.enums;

import com.growingpots.domain.transcript.entity.GraduationAnalysisSummary;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GraduationConditionType {

    MAJOR_BASIC("전공 기초", "CREDITS", true, true,
            GraduationAnalysisSummary::getMajorBasicCurrent,
            GraduationAnalysisSummary::getMajorBasicRequired),

    MAJOR_REQUIRED("전공 필수", "CREDITS", true, true,
            GraduationAnalysisSummary::getMajorRequiredCurrent,
            GraduationAnalysisSummary::getMajorRequiredRequired),

    MAJOR_ELECTIVE("전공 선택", "CREDITS", true, true,
            GraduationAnalysisSummary::getMajorElectiveCurrent,
            GraduationAnalysisSummary::getMajorElectiveRequired),

    REQUIRED_GE("필수 교과", "CREDITS", false, true,
            GraduationAnalysisSummary::getRequiredGeCurrent,
            GraduationAnalysisSummary::getRequiredGeRequired),

    DISTRIBUTED_GE("배분 이수 교과", "CREDITS", false, true,
            GraduationAnalysisSummary::getDistributedGeCurrent,
            GraduationAnalysisSummary::getDistributedGeRequired),

    FREE_GE("자유 이수 교과", "CREDITS", false, true,
            GraduationAnalysisSummary::getFreeGeCurrent,
            GraduationAnalysisSummary::getFreeGeRequired),

    // required 컬럼 없음 → required=null, satisfied=true 고정. 원형 차트 8등분에서 제외(chartTarget=false)
    GENERAL_ELECTIVE("일반 선택", "CREDITS", false, false,
            GraduationAnalysisSummary::getGeneralElectiveCurrent,
            null),

    // Division 기반이 아닌 Course.isEnglish/isSw 플래그로 조회
    ENGLISH_COURSE("영어 강의", "COURSES", false, true,
            GraduationAnalysisSummary::getEnglishCurrent,
            GraduationAnalysisSummary::getEnglishRequired),

    SW_CERT_COURSE("SW 인증 강의", "CREDITS", false, true,
            s -> s.getSwCertCurrent() != null ? s.getSwCertCurrent() : 0,
            s -> s.getSwCertRequired() != null ? s.getSwCertRequired() : 0),

    // 학과 자체의 독립 졸업요건(division 기반 아님, GraduationAnalysisSummary 스냅샷도 없음).
    // 해당 학과에만 존재하고(예: 스포츠의학과 졸업필수), RequirementCourse(division=null)로 정의된
    // 하위조건들을 학생 이수내역과 실시간 대조해 판정한다. current/requiredExtractor는 이 타입에서는
    // GraduationService가 완전히 별도 처리해서 호출되지 않는 자리표시자다.
    GRADUATION_REQUIRED("졸업필수", "CONDITIONS", false, false,
            s -> 0, s -> 0);

    private final String displayName;
    private final String unit;
    private final boolean aggregatable;
    // false인 항목은 원형 차트 8등분에서 제외 (현재 GENERAL_ELECTIVE만 해당)
    private final boolean chartTarget;
    private final ToIntFunction<GraduationAnalysisSummary> currentExtractor;
    // null이면 required 컬럼 없음 (GENERAL_ELECTIVE)
    private final Function<GraduationAnalysisSummary, Integer> requiredExtractor;
}