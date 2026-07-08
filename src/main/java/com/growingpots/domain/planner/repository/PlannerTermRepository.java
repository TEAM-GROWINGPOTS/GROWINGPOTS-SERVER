package com.growingpots.domain.planner.repository;

import com.growingpots.domain.planner.entity.PlannerSimulation;
import com.growingpots.domain.planner.entity.PlannerTerm;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PlannerTermRepository extends JpaRepository<PlannerTerm, Long> {

    List<PlannerTerm> findByPlannerSimulationOrderByTermOrder(PlannerSimulation plannerSimulation);

    @Query("SELECT pt.id FROM PlannerTerm pt WHERE pt.plannerSimulation.id = :simulationId")
    List<Long> findIdsByPlannerSimulationId(@Param("simulationId") Long simulationId);

    @Query("SELECT pt FROM PlannerTerm pt "
            + "JOIN FETCH pt.plannerSimulation ps "
            + "JOIN FETCH ps.studentProfile sp "
            + "WHERE pt.id = :termId")
    Optional<PlannerTerm> findWithOwnerById(@Param("termId") Long termId);

    @Transactional
    @Modifying
    @Query("DELETE FROM PlannerTerm pt WHERE pt.plannerSimulation.id = :simulationId")
    void deleteAllByPlannerSimulationId(@Param("simulationId") Long simulationId);
}