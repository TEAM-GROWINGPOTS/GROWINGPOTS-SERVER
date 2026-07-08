package com.growingpots.domain.planner.repository;

import com.growingpots.domain.planner.entity.PlannerTermVersion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PlannerTermVersionRepository extends JpaRepository<PlannerTermVersion, Long> {

    @Query("SELECT ptv.id FROM PlannerTermVersion ptv WHERE ptv.plannerTerm.id IN :termIds")
    List<Long> findIdsByPlannerTermIdIn(@Param("termIds") List<Long> termIds);

    @Transactional
    @Modifying
    @Query("DELETE FROM PlannerTermVersion ptv WHERE ptv.plannerTerm.id IN :termIds")
    void deleteAllByPlannerTermIdIn(@Param("termIds") List<Long> termIds);
}