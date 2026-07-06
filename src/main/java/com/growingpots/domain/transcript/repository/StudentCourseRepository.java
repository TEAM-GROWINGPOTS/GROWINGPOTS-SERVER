package com.growingpots.domain.transcript.repository;

import com.growingpots.domain.transcript.entity.enums.RecordSource;
import com.growingpots.domain.transcript.entity.StudentCourse;
import com.growingpots.domain.user.entity.StudentProfile;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentCourseRepository extends JpaRepository<StudentCourse, Long> {
    void deleteByStudentProfileAndSource(StudentProfile studentProfile, RecordSource source);

    @Query("SELECT sc FROM StudentCourse sc LEFT JOIN FETCH sc.course c LEFT JOIN FETCH c.offeringDepartment "
            + "WHERE sc.studentProfile = :studentProfile")
    List<StudentCourse> findWithCourseByStudentProfile(@Param("studentProfile") StudentProfile studentProfile);
}
