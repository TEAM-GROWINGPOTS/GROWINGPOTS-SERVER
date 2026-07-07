package com.growingpots.domain.university.entity.enums;

public enum DivisionCategory {
    MAJOR_BASIC("전공기초"),
    MAJOR_REQUIRED("전공필수"),
    MAJOR_ELECTIVE("전공선택"),
    REQUIRED_GE("필수교과"),
    DISTRIBUTED_GE("배분이수교과"),
    FREE_GE("자유이수교과"),
    GENERAL_ELECTIVE("일반선택");

    private final String displayName;

    DivisionCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
