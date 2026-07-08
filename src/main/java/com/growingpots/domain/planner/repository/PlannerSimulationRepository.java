package com.growingpots.domain.planner.repository;

import com.growingpots.domain.planner.entity.PlannerSimulation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlannerSimulationRepository extends JpaRepository<PlannerSimulation, Long> {
}