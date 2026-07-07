package com.growingpots.domain.university.repository;

import com.growingpots.domain.university.entity.Division;
import com.growingpots.domain.university.entity.School;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DivisionRepository extends JpaRepository<Division, Long> {

    Optional<Division> findBySchoolAndCode(School school, String code);

    List<Division> findBySchoolAndCategory(School school, String category);
}