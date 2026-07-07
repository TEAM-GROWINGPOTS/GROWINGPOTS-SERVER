package com.growingpots.domain.university.repository;

import com.growingpots.domain.university.entity.Division;
import com.growingpots.domain.university.entity.School;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DivisionRepository extends JpaRepository<Division, Long> {
    List<Division> findBySchool(School school);
}
