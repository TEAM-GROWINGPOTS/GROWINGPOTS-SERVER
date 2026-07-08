package com.growingpots.domain.university.dto.request;

import com.growingpots.domain.university.entity.enums.CourseDivisionFilter;
import com.growingpots.domain.university.entity.enums.OpenedSemester;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;

// 같은 필드 안의 값끼리는 OR(합집합), 서로 다른 필드끼리는 AND(교집합)로 결합한다.
public record CourseSearchRequest(
        String keyword,
        String collegeName,
        Long departmentId,
        List<CourseDivisionFilter> divisionCategory,
        List<Integer> year,
        List<OpenedSemester> semester,
        List<Integer> credits,
        String campus,
        @Min(0) Integer page,
        @Min(1) @Max(100) Integer size
) {
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    public int pageOrDefault() {
        return page != null ? page : DEFAULT_PAGE;
    }

    public int sizeOrDefault() {
        return size != null ? size : DEFAULT_SIZE;
    }
}
