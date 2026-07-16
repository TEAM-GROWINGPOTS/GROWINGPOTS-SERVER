package com.growingpots.domain.planner.repository;

import com.growingpots.domain.planner.entity.PlannerSimulation;
import com.growingpots.domain.planner.entity.PlannerTermVersion;
import com.growingpots.domain.planner.entity.PlannerVersionItem;
import com.growingpots.domain.user.entity.StudentProfile;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PlannerVersionItemRepository extends JpaRepository<PlannerVersionItem, Long> {

    @Query("SELECT pvi FROM PlannerVersionItem pvi "
            + "JOIN FETCH pvi.course c LEFT JOIN FETCH c.offeringDepartment "
            + "LEFT JOIN FETCH pvi.plannedDivision "
            + "WHERE pvi.plannerTermVersion IN :versions "
            + "ORDER BY pvi.plannerTermVersion.id, pvi.coursePositionOrder")
    List<PlannerVersionItem> findWithDetailsByPlannerTermVersionIn(@Param("versions") List<PlannerTermVersion> versions);

    // 학생의 플래너에서 현재 선택된 버전의 계획 과목 전체 조회 (source=PLANNED 졸업현황 계산용)
    // geArea까지 JOIN FETCH: DISTRIBUTED_GE 영역 판정 시 N+1 방지
    @Query("SELECT pvi FROM PlannerVersionItem pvi "
            + "JOIN FETCH pvi.course c LEFT JOIN FETCH c.offeringDepartment "
            + "LEFT JOIN FETCH c.geArea "
            + "LEFT JOIN FETCH pvi.plannedDivision "
            + "WHERE pvi.plannerTermVersion.isSelected = true "
            + "AND pvi.plannerTermVersion.plannerTerm.plannerSimulation.studentProfile = :profile")
    List<PlannerVersionItem> findSelectedByStudentProfile(@Param("profile") StudentProfile profile);

    @Query("SELECT DISTINCT pvi.course.id FROM PlannerVersionItem pvi "
            + "WHERE pvi.plannerTermVersion.isSelected = true "
            + "AND pvi.plannerTermVersion.plannerTerm.plannerSimulation = :simulation "
            + "AND (pvi.plannerTermVersion.plannerTerm.yearLevel * 10 + pvi.plannerTermVersion.plannerTerm.semester) "
            + "< (:yearLevel * 10 + :semester)")
    List<Long> findCourseIdsInEarlierTerms(
            @Param("simulation") PlannerSimulation simulation,
            @Param("yearLevel") int yearLevel,
            @Param("semester") int semester);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM PlannerVersionItem pvi WHERE pvi.plannerTermVersion.id IN :versionIds")
    void deleteAllByPlannerTermVersionIdIn(@Param("versionIds") List<Long> versionIds);
}