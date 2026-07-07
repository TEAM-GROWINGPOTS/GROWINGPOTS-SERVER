package com.growingpots.domain.transcript.repository;

import com.growingpots.domain.transcript.entity.StudentCourse;
import com.growingpots.domain.transcript.entity.enums.CourseStatus;
import com.growingpots.domain.transcript.entity.enums.RecordSource;
import com.growingpots.domain.university.entity.Division;
import com.growingpots.domain.user.entity.StudentProfile;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentCourseRepository extends JpaRepository<StudentCourse, Long> {

    void deleteByStudentProfileAndSource(StudentProfile studentProfile, RecordSource source);

    List<StudentCourse> findByStudentProfile(StudentProfile studentProfile);

    @Query("SELECT sc FROM StudentCourse sc LEFT JOIN FETCH sc.course c LEFT JOIN FETCH c.offeringDepartment "
            + "WHERE sc.studentProfile = :studentProfile")
    List<StudentCourse> findWithCourseByStudentProfile(@Param("studentProfile") StudentProfile studentProfile);

    @Query("SELECT sc FROM StudentCourse sc LEFT JOIN FETCH sc.course c LEFT JOIN FETCH c.offeringDepartment "
            + "WHERE sc.studentProfile = :studentProfile AND sc.appliedDivision IN :divisions")
    List<StudentCourse> findByStudentProfileAndAppliedDivisionIn(
            @Param("studentProfile") StudentProfile studentProfile,
            @Param("divisions") List<Division> divisions);

    @Query("SELECT sc FROM StudentCourse sc JOIN FETCH sc.course c LEFT JOIN FETCH c.offeringDepartment "
            + "WHERE sc.studentProfile = :studentProfile AND c.isEnglish = true")
    List<StudentCourse> findByStudentProfileAndCourseIsEnglish(
            @Param("studentProfile") StudentProfile studentProfile);

    @Query("SELECT sc FROM StudentCourse sc JOIN FETCH sc.course c LEFT JOIN FETCH c.offeringDepartment "
            + "WHERE sc.studentProfile = :studentProfile AND c.isSw = true")
    List<StudentCourse> findByStudentProfileAndCourseIsSw(
            @Param("studentProfile") StudentProfile studentProfile);

    @Query("SELECT sc.course.id FROM StudentCourse sc "
            + "WHERE sc.studentProfile = :studentProfile AND sc.status = :status AND sc.course IS NOT NULL")
    List<Long> findCourseIdsByStudentProfileAndStatus(
            @Param("studentProfile") StudentProfile studentProfile, @Param("status") CourseStatus status);
}
