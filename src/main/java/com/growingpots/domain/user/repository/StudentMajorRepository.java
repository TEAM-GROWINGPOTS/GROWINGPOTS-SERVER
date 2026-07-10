package com.growingpots.domain.user.repository;

import com.growingpots.domain.university.entity.Department;
import com.growingpots.domain.user.entity.StudentMajor;
import com.growingpots.domain.user.entity.StudentProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StudentMajorRepository extends JpaRepository<StudentMajor, Long> {
    Optional<StudentMajor> findByStudentProfileAndDepartment(StudentProfile studentProfile, Department department);

    // id 오름차순 = 생성 순서. 본전공은 온보딩 때 가장 먼저 만들어지고 복수전공은 그 뒤에(PDF 분석
    // 시) 추가되므로, 이 정렬만으로 항상 본전공이 첫 번째로 온다(재업로드 시에도 기존 학과의
    // StudentMajor row는 재사용되지 재생성되지 않아 id가 안 바뀜 - TranscriptPersister 참고).
    @Query("SELECT sm FROM StudentMajor sm JOIN FETCH sm.department LEFT JOIN FETCH sm.track "
            + "WHERE sm.studentProfile = :studentProfile ORDER BY sm.id ASC")
    List<StudentMajor> findWithDepartmentByStudentProfile(@Param("studentProfile") StudentProfile studentProfile);
}
