package com.growingpots.domain.user.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StudentProfileCreateResponse {

    private final Long studentProfileId;
    private final MainMajorInfo mainMajor;

    @Getter
    @Builder
    public static class MainMajorInfo {
        private final Long studentMajorId;
        private final String departmentName;
    }
}