package com.growingpots.domain.planner.repository;

import com.growingpots.domain.planner.entity.PlannerVersionItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PlannerVersionItemRepository extends JpaRepository<PlannerVersionItem, Long> {

    @Transactional
    @Modifying
    @Query("DELETE FROM PlannerVersionItem pvi WHERE pvi.plannerTermVersion.id IN :versionIds")
    void deleteAllByPlannerTermVersionIdIn(@Param("versionIds") List<Long> versionIds);
}