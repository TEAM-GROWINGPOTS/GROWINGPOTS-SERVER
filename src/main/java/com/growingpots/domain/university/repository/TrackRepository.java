package com.growingpots.domain.university.repository;

import com.growingpots.domain.university.entity.Track;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrackRepository extends JpaRepository<Track, Long> {
}