package com.growingpots.domain.university.specification;

import com.growingpots.domain.university.entity.Course;
import com.growingpots.domain.university.entity.School;
import com.growingpots.domain.university.entity.enums.DivisionCategory;
import com.growingpots.domain.university.entity.enums.OpenedSemester;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

// 필드 하나(예: divisionCategory)의 여러 선택값은 OR로 묶고, 여기서 만든 Specification들끼리는
// CourseSpecificationsBuilder에서 and()로 묶는다 (같은 필드 안에서 합집합, 필드 간 교집합).
public class CourseSpecifications {

    private CourseSpecifications() {
    }

    public static Specification<Course> withSchool(School school) {
        return (root, query, cb) -> cb.equal(root.get("school"), school);
    }

    // offeringDepartment/defaultDivision은 LAZY라 fetch join 없이 응답 매핑 시 접근하면 페이지당 N+1이
    // 발생한다. count 쿼리(결과 타입이 Long)에는 fetch join을 걸면 안 되므로 content 쿼리에만 적용한다.
    // to-one 연관관계라 페이지네이션과 같이 써도 안전하다(컬렉션 fetch join과 달리 행이 늘어나지 않음).
    public static Specification<Course> withFetchedAssociations() {
        return (root, query, cb) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("offeringDepartment", JoinType.LEFT);
                root.fetch("defaultDivision", JoinType.LEFT);
            }
            return cb.conjunction();
        };
    }

    public static Specification<Course> withKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String pattern = "%" + keyword + "%";
        return (root, query, cb) -> cb.or(
                cb.like(root.get("name"), pattern),
                cb.like(root.get("courseCode"), pattern)
        );
    }

    public static Specification<Course> withCollegeName(String collegeName) {
        if (collegeName == null || collegeName.isBlank()) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("offeringDepartment").get("college"), collegeName);
    }

    public static Specification<Course> withDepartmentId(Long departmentId) {
        if (departmentId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("offeringDepartment").get("id"), departmentId);
    }

    // recommendedYearLow/High가 null인 과목(권장 학년 자체가 없는 과목)은 SQL의 NULL 비교가 전부
    // UNKNOWN으로 처리되어 아래 range 비교에 그대로 걸리면 학년 필터를 걸 때마다 결과에서 빠진다.
    // "권장 학년 제한 없음"으로 보고 어떤 학년으로 필터링해도 항상 매칭되게 한다.
    public static Specification<Course> withYears(List<Integer> years) {
        if (years == null || years.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> {
            Predicate noRestriction = cb.isNull(root.get("recommendedYearLow"));
            Predicate[] rangePredicates = years.stream()
                    .map(year -> cb.and(
                            cb.lessThanOrEqualTo(root.get("recommendedYearLow"), year),
                            cb.greaterThanOrEqualTo(root.get("recommendedYearHigh"), year)))
                    .toArray(Predicate[]::new);
            return cb.or(noRestriction, cb.or(rangePredicates));
        };
    }

    public static Specification<Course> withSemesters(List<OpenedSemester> semesters) {
        if (semesters == null || semesters.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> root.get("openedSemester").in(semesters);
    }

    // credits 중 4가 있으면 "4학점 이상"(>=4)으로 처리하고, 나머지는 정확히 일치하는 값으로 OR 결합
    public static Specification<Course> withCredits(List<Integer> credits) {
        if (credits == null || credits.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            for (Integer credit : credits) {
                if (credit >= 4) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("credit"), 4));
                } else {
                    predicates.add(cb.equal(root.get("credit"), credit));
                }
            }
            return cb.or(predicates.toArray(new Predicate[0]));
        };
    }

    // divisionCategory 필터 전체(일반 카테고리 + CROSS_MAJOR)를 하나의 OR로 묶는다.
    // crossMajorRequested가 true인데 해당 학과 인정 과목이 하나도 없으면 이 조건만으로는 매칭되는 게
    // 없어야 하므로 cb.disjunction()(항상 거짓)을 넣어 빈 IN() 대신 명시적으로 처리한다.
    public static Specification<Course> withDivisionFilters(
            List<DivisionCategory> categories, boolean crossMajorRequested, List<Long> crossMajorCourseIds) {
        boolean hasCategories = categories != null && !categories.isEmpty();
        if (!hasCategories && !crossMajorRequested) {
            return null;
        }
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (hasCategories) {
                predicates.add(root.get("defaultDivision").get("category").in(categories));
            }
            if (crossMajorRequested) {
                predicates.add(crossMajorCourseIds.isEmpty()
                        ? cb.disjunction()
                        : root.get("id").in(crossMajorCourseIds));
            }
            return cb.or(predicates.toArray(new Predicate[0]));
        };
    }
}
