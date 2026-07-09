package com.growingpots.domain.graduation.dto.response;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GraduationResponse {

    private final Summary summary;
    // 모든 졸업요건(required가 있는 항목 전부)을 충족한 경우 true. 탭 구분 없이 항상 전체 기준으로 계산됨.
    private final boolean graduatable;
    // PRIMARY/MULTI/GE/OTHERS 탭: conditions 사용, sections=null
    private final List<ConditionInfo> conditions;
    // PRIMARY/MULTI 탭에서 해당 학과에 독립 졸업요건이 있을 때만 채워짐. GE/OTHERS 탭·ALL 탭(top-level)은 null
    // (ALL 탭은 sections.primary/multi 안의 graduationRequired를 대신 본다).
    private final GraduationRequiredSummary graduationRequired;
    // ALL 탭: sections 사용, conditions=null
    private final AllSections sections;
    private final List<CertInfo> certs;

    @Getter
    @Builder
    public static class Summary {
        private final CreditInfo totalCredits;
        private final GpaInfo gpa;
        private final String enrollmentStatus;
    }

    @Getter
    @AllArgsConstructor
    public static class CreditInfo {
        private final int current;
        private final int required;
    }

    @Getter
    @AllArgsConstructor
    public static class GpaInfo {
        private final BigDecimal current;
        private final BigDecimal min;
    }

    @Getter
    @Builder
    public static class ConditionInfo {
        private final String code;
        private final String name;
        private final int current;
        private final Integer required; // GENERAL_ELECTIVE는 null
        private final String unit;
        private final boolean satisfied;
        private final boolean chartTarget; // false면 원형 차트 8등분에서 제외
    }

    @Getter
    @AllArgsConstructor
    public static class CertInfo {
        private final String certType;
        private final String result;
    }

    // ALL 탭 전용: 4개 섹션을 분리해서 반환
    @Getter
    @Builder
    public static class AllSections {
        private final TabSection primary;
        private final TabSection multi;   // 복수전공 없으면 null
        private final TabSection ge;
        private final TabSection others;
    }

    @Getter
    @Builder
    public static class TabSection {
        private final String majorName; // 전공 섹션(primary/multi)만 채워짐, ge/others는 null
        private final List<ConditionInfo> conditions;
        // 전공 섹션(primary/multi)에서, 해당 학과에 독립 졸업요건(예: 스포츠의학과 졸업필수)이 있을
        // 때만 채워짐. 없는 학과·ge/others 섹션은 null.
        private final GraduationRequiredSummary graduationRequired;
    }

    @Getter
    @Builder
    public static class GraduationRequiredSummary {
        private final boolean satisfied;
        // 이 졸업요건에 연결된 과목들 중 이수(COMPLETED)한 것들의 학점 합계
        private final int totalCredit;
        private final List<String> unmetDescriptions;
    }
}