package com.growingpots.domain.university.repository;

import com.growingpots.domain.university.entity.GeArea;
import com.growingpots.domain.university.entity.School;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GeAreaRepository extends JpaRepository<GeArea, Long> {
    List<GeArea> findBySchool(School school);
}
