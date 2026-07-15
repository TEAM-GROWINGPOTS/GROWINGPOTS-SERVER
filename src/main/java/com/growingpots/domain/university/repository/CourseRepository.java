package com.growingpots.domain.university.repository;

import com.growingpots.domain.university.entity.Course;
import com.growingpots.domain.university.entity.Department;
import com.growingpots.domain.university.entity.Division;
import com.growingpots.domain.university.entity.School;
import com.growingpots.domain.university.entity.enums.DivisionCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseRepository extends JpaRepository<Course, Long>, JpaSpecificationExecutor<Course> {
    Optional<Course> findByCourseCode(String courseCode);
    List<Course> findBySchool(School school);

    // 이수/미이수 조회에서 "이 학과+이수구분의 미이수 후보"로 쓴다. 커리큘럼 개정으로 폐지된 과목은
    // isActive=false로만 표시하고 지우지 않으므로(과거 이수기록 매칭용), 여기선 제외해야 학생이
    // 플래너에서 추가할 수 없는 과목을 "미이수"라고 보여주는 모순이 안 생긴다.
    @Query("SELECT c FROM Course c WHERE c.offeringDepartment = :department "
            + "AND c.defaultDivision.category = :category AND c.isActive = true")
    List<Course> findActiveByDepartmentAndDivisionCategory(
            @Param("department") Department department,
            @Param("category") DivisionCategory category);

    // 필수교과(REQUIRED_GE)처럼 학과가 아닌 이수구분(Division) 단위로 필수 과목을 조회할 때 사용.
    // 학교 공통 교양은 offeringDepartment가 학생 소속 학과와 일치하지 않아 위 메서드로 조회 불가.
    @Query("SELECT c FROM Course c WHERE c.defaultDivision = :division AND c.isActive = true")
    List<Course> findActiveByDefaultDivision(@Param("division") Division division);
}
