package com.growingpots.domain.planner.repository;

import com.growingpots.domain.planner.entity.PlannerSimulation;
import com.growingpots.domain.user.entity.StudentProfile;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlannerSimulationRepository extends JpaRepository<PlannerSimulation, Long> {

    Optional<PlannerSimulation> findByStudentProfile(StudentProfile studentProfile);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ps FROM PlannerSimulation ps WHERE ps.studentProfile = :profile")
    Optional<PlannerSimulation> findByStudentProfileForUpdate(@Param("profile") StudentProfile profile);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ps FROM PlannerSimulation ps WHERE ps.id = :id")
    Optional<PlannerSimulation> findByIdForUpdate(@Param("id") Long id);
}