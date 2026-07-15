package com.growingpots.domain.planner.repository;

import com.growingpots.domain.planner.entity.PlannerSimulation;
import com.growingpots.domain.user.entity.StudentProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlannerSimulationRepository extends JpaRepository<PlannerSimulation, Long> {

    Optional<PlannerSimulation> findByStudentProfile(StudentProfile studentProfile);
}