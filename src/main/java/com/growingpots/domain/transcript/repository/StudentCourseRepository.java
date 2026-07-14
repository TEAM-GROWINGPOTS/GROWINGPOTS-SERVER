package com.growingpots.domain.transcript.repository;

import com.growingpots.domain.transcript.entity.StudentCourse;
import com.growingpots.domain.transcript.entity.enums.CourseStatus;
import com.growingpots.domain.transcript.entity.enums.RecordSource;
import com.growingpots.domain.university.entity.Department;
import com.growingpots.domain.university.entity.Division;
import com.growingpots.domain.university.entity.enums.DivisionCategory;
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

    // 영어/SW는 완료(COMPLETED) 또는 진행중(IN_PROGRESS, 현재 수강신청된 과목)까지 포함한다 - PDF
    // 스냅샷(GraduationAnalysisSummary.englishCurrent 등)이 진행중 과목까지 세는 학교 공식 집계 방식과
    // 최대한 맞추기 위함. 같은 과목을 재수강 중이면 같은 courseCode로 여러 행이 나올 수 있는데, 그건
    // 서비스 레이어(GraduationService)에서 과목 단위로 중복 제거한다(#188 연장선에서 발견).
    @Query("SELECT sc FROM StudentCourse sc JOIN FETCH sc.course c LEFT JOIN FETCH c.offeringDepartment "
            + "WHERE sc.studentProfile = :studentProfile AND c.isEnglish = true "
            + "AND sc.status IN (com.growingpots.domain.transcript.entity.enums.CourseStatus.COMPLETED, com.growingpots.domain.transcript.entity.enums.CourseStatus.IN_PROGRESS)")
    List<StudentCourse> findByStudentProfileAndCourseIsEnglish(
            @Param("studentProfile") StudentProfile studentProfile);

    @Query("SELECT sc FROM StudentCourse sc JOIN FETCH sc.course c LEFT JOIN FETCH c.offeringDepartment "
            + "WHERE sc.studentProfile = :studentProfile AND c.isSw = true "
            + "AND sc.status IN (com.growingpots.domain.transcript.entity.enums.CourseStatus.COMPLETED, com.growingpots.domain.transcript.entity.enums.CourseStatus.IN_PROGRESS)")
    List<StudentCourse> findByStudentProfileAndCourseIsSw(
            @Param("studentProfile") StudentProfile studentProfile);

    // 영어강의 중 특정 이수구분 카테고리에 해당하는 것만 조회 (GE/OTHERS 탭용).
    @Query("SELECT sc FROM StudentCourse sc JOIN FETCH sc.course c LEFT JOIN FETCH c.offeringDepartment "
            + "WHERE sc.studentProfile = :studentProfile AND c.isEnglish = true "
            + "AND sc.status IN (com.growingpots.domain.transcript.entity.enums.CourseStatus.COMPLETED, com.growingpots.domain.transcript.entity.enums.CourseStatus.IN_PROGRESS) "
            + "AND sc.appliedDivision IS NOT NULL AND sc.appliedDivision.category IN :categories")
    List<StudentCourse> findByStudentProfileAndCourseIsEnglishAndDivisionCategoryIn(
            @Param("studentProfile") StudentProfile studentProfile,
            @Param("categories") List<DivisionCategory> categories);

    // SW인증강의 중 특정 이수구분 카테고리에 해당하는 것만 조회 (GE/OTHERS 탭용).
    @Query("SELECT sc FROM StudentCourse sc JOIN FETCH sc.course c LEFT JOIN FETCH c.offeringDepartment "
            + "WHERE sc.studentProfile = :studentProfile AND c.isSw = true "
            + "AND sc.status IN (com.growingpots.domain.transcript.entity.enums.CourseStatus.COMPLETED, com.growingpots.domain.transcript.entity.enums.CourseStatus.IN_PROGRESS) "
            + "AND sc.appliedDivision IS NOT NULL AND sc.appliedDivision.category IN :categories")
    List<StudentCourse> findByStudentProfileAndCourseIsSwAndDivisionCategoryIn(
            @Param("studentProfile") StudentProfile studentProfile,
            @Param("categories") List<DivisionCategory> categories);

    // 영어강의 중 특정 이수구분 + 개설학과 기준 조회 (studentMajorId로 특정 전공 하나만 조회할 때용)
    // appliedDepartment가 있으면 우선 적용, 없으면 course.offeringDepartment로 판별.
    @Query("SELECT sc FROM StudentCourse sc JOIN FETCH sc.course c LEFT JOIN FETCH c.offeringDepartment "
            + "WHERE sc.studentProfile = :studentProfile AND c.isEnglish = true "
            + "AND sc.status IN (com.growingpots.domain.transcript.entity.enums.CourseStatus.COMPLETED, com.growingpots.domain.transcript.entity.enums.CourseStatus.IN_PROGRESS) "
            + "AND sc.appliedDivision IS NOT NULL AND sc.appliedDivision.category IN :categories "
            + "AND (sc.appliedDepartment = :department "
            + "     OR (sc.appliedDepartment IS NULL AND c.offeringDepartment = :department))")
    List<StudentCourse> findByStudentProfileAndCourseIsEnglishAndDivisionCategoryInAndDepartment(
            @Param("studentProfile") StudentProfile studentProfile,
            @Param("categories") List<DivisionCategory> categories,
            @Param("department") Department department);

    // SW인증강의 중 특정 이수구분 + 개설학과 기준 조회 (studentMajorId로 특정 전공 하나만 조회할 때용).
    @Query("SELECT sc FROM StudentCourse sc JOIN FETCH sc.course c LEFT JOIN FETCH c.offeringDepartment "
            + "WHERE sc.studentProfile = :studentProfile AND c.isSw = true "
            + "AND sc.status IN (com.growingpots.domain.transcript.entity.enums.CourseStatus.COMPLETED, com.growingpots.domain.transcript.entity.enums.CourseStatus.IN_PROGRESS) "
            + "AND sc.appliedDivision IS NOT NULL AND sc.appliedDivision.category IN :categories "
            + "AND (sc.appliedDepartment = :department "
            + "     OR (sc.appliedDepartment IS NULL AND c.offeringDepartment = :department))")
    List<StudentCourse> findByStudentProfileAndCourseIsSwAndDivisionCategoryInAndDepartment(
            @Param("studentProfile") StudentProfile studentProfile,
            @Param("categories") List<DivisionCategory> categories,
            @Param("department") Department department);

    @Query("SELECT sc.course.id FROM StudentCourse sc "
            + "WHERE sc.studentProfile = :studentProfile AND sc.status = :status AND sc.course IS NOT NULL")
    List<Long> findCourseIdsByStudentProfileAndStatus(
            @Param("studentProfile") StudentProfile studentProfile, @Param("status") CourseStatus status);

    // 학기 플래너 조회용. appliedDivision을 INNER JOIN FETCH하므로 이수구분 검수가 끝나지 않은
    // (appliedDivision == null) 과목은 결과에서 빠진다 — 플래너엔 검수 완료된 과목만 노출된다는 전제.
    @Query("SELECT sc FROM StudentCourse sc LEFT JOIN FETCH sc.course c LEFT JOIN FETCH c.offeringDepartment "
            + "JOIN FETCH sc.appliedDivision WHERE sc.studentProfile = :studentProfile")
    List<StudentCourse> findWithCourseAndDivisionByStudentProfile(@Param("studentProfile") StudentProfile studentProfile);

    @Query("SELECT sc.course.id FROM StudentCourse sc "
            + "WHERE sc.studentProfile = :studentProfile "
            + "AND sc.status IN :statuses "
            + "AND sc.course IS NOT NULL")
    List<Long> findCourseIdsByStudentProfileAndStatusIn(
            @Param("studentProfile") StudentProfile studentProfile,
            @Param("statuses") List<CourseStatus> statuses);

    // DISTRIBUTED_GE 과목 조회 시 course.geArea까지 JOIN FETCH (영역 판정용)
    @Query("SELECT sc FROM StudentCourse sc "
            + "LEFT JOIN FETCH sc.course c "
            + "LEFT JOIN FETCH c.geArea "
            + "LEFT JOIN FETCH c.offeringDepartment "
            + "WHERE sc.studentProfile = :studentProfile "
            + "AND sc.appliedDivision = :division")
    List<StudentCourse> findByStudentProfileAndAppliedDivisionWithGeArea(
            @Param("studentProfile") StudentProfile studentProfile,
            @Param("division") Division division);
}
