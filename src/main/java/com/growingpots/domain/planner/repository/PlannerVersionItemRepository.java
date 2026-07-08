package com.growingpots.domain.planner.repository;

import com.growingpots.domain.planner.entity.PlannerTermVersion;
import com.growingpots.domain.planner.entity.PlannerVersionItem;
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
            + "ORDER BY pvi.plannerTermVersion.id, pvi.positionOrder")
    List<PlannerVersionItem> findWithDetailsByPlannerTermVersionIn(@Param("versions") List<PlannerTermVersion> versions);

    @Transactional
    @Modifying
    @Query("DELETE FROM PlannerVersionItem pvi WHERE pvi.plannerTermVersion.id IN :versionIds")
    void deleteAllByPlannerTermVersionIdIn(@Param("versionIds") List<Long> versionIds);
}