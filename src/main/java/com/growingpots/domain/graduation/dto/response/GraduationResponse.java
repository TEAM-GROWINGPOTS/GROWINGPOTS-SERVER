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
    private final List<ConditionInfo> conditions;
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
}