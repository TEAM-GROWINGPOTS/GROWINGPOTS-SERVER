package com.growingpots.domain.university.repository;

import com.growingpots.domain.university.entity.Division;
import com.growingpots.domain.university.entity.School;
import com.growingpots.domain.university.entity.enums.DivisionCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DivisionRepository extends JpaRepository<Division, Long> {

    List<Division> findBySchool(School school);

    Optional<Division> findBySchoolAndCategory(School school, DivisionCategory category);
}