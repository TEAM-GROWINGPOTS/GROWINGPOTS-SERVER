package com.growingpots.domain.user.service;

import com.growingpots.domain.transcript.entity.StudentCourse;
import com.growingpots.domain.transcript.entity.enums.CourseStatus;
import com.growingpots.domain.transcript.entity.enums.RecordSource;
import com.growingpots.domain.transcript.entity.enums.Semester;
import com.growingpots.domain.transcript.repository.StudentCourseRepository;
import com.growingpots.domain.university.entity.Course;
import com.growingpots.domain.university.entity.Department;
import com.growingpots.domain.university.entity.Division;
import com.growingpots.domain.university.entity.School;
import com.growingpots.domain.university.entity.enums.DivisionCategory;
import com.growingpots.domain.university.repository.CourseRepository;
import com.growingpots.domain.university.repository.DepartmentRepository;
import com.growingpots.domain.university.repository.DivisionRepository;
import com.growingpots.domain.university.repository.SchoolRepository;
import com.growingpots.domain.user.dto.request.StudentCourseUpdateRequest;
import com.growingpots.domain.user.dto.request.StudentCourseUpdateRequest.CourseUpdateItem;
import com.growingpots.domain.user.dto.request.StudentProfileCreateRequest;
import com.growingpots.domain.user.dto.response.StudentCourseListResponse;
import com.growingpots.domain.user.dto.response.StudentProfileCreateResponse;
import com.growingpots.domain.user.dto.response.StudentProfileResponse;
import com.growingpots.domain.user.entity.Member;
import com.growingpots.domain.user.entity.StudentMajor;
import com.growingpots.domain.user.entity.StudentMajor.MajorType;
import com.growingpots.domain.user.entity.StudentProfile;
import com.growingpots.domain.user.repository.MemberRepository;
import com.growingpots.domain.user.repository.StudentMajorRepository;
import com.growingpots.domain.user.repository.StudentProfileRepository;
import com.growingpots.global.exception.BaseException;
import com.growingpots.global.response.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudentProfileService {

    private final MemberRepository memberRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final StudentMajorRepository studentMajorRepository;
    private final StudentCourseRepository studentCourseRepository;
    private final SchoolRepository schoolRepository;
    private final DepartmentRepository departmentRepository;
    private final CourseRepository courseRepository;
    private final DivisionRepository divisionRepository;

    @Transactional
    public StudentProfileCreateResponse create(Long memberId, StudentProfileCreateRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

        if (studentProfileRepository.existsByMember(member)) {
            throw new BaseException(ErrorCode.STUDENT_PROFILE_ALREADY_EXISTS);
        }

        School school = schoolRepository.findById(request.schoolId())
                .orElseThrow(() -> new BaseException(ErrorCode.UNIVERSITY_NOT_FOUND));

        Department department = departmentRepository.findById(request.departmentId())
                .orElseThrow(() -> new BaseException(ErrorCode.MAJOR_NOT_FOUND));

        if (!department.getSchool().getId().equals(school.getId())) {
            throw new BaseException(ErrorCode.DEPARTMENT_NOT_IN_SCHOOL);
        }

        StudentProfile studentProfile = studentProfileRepository.save(
                StudentProfile.builder()
                        .member(member)
                        .school(school)
                        .department(department)
                        .admissionYear(request.admissionYear())
                        .build()
        );

        StudentMajor studentMajor = studentMajorRepository.save(
                StudentMajor.builder()
                        .studentProfile(studentProfile)
                        .department(department)
                        .majorType(MajorType.MAIN)
                        .track(null)
                        .build()
        );

        return StudentProfileCreateResponse.builder()
                .studentProfileId(studentProfile.getId())
                .mainMajor(StudentProfileCreateResponse.MainMajorInfo.builder()
                        .studentMajorId(studentMajor.getId())
                        .departmentName(department.getName())
                        .build())
                .build();
    }

    @Transactional(readOnly = true)
    public StudentProfileResponse getMyProfile(Long memberId) {
        StudentProfile profile = studentProfileRepository.findWithDetailsByMemberId(memberId)
                .orElseThrow(() -> new BaseException(ErrorCode.STUDENT_PROFILE_NOT_FOUND));

        List<StudentMajor> majors = studentMajorRepository.findWithDepartmentByStudentProfile(profile);

        List<StudentProfileResponse.MajorInfo> majorInfos = majors.stream()
                .map(sm -> StudentProfileResponse.MajorInfo.builder()
                        .studentMajorId(sm.getId())
                        .majorType(sm.getMajorType().name())
                        .departmentName(sm.getDepartment().getName())
                        .trackName(sm.getTrack() != null ? sm.getTrack().getName() : null)
                        .build())
                .toList();

        return StudentProfileResponse.builder()
                .studentProfileId(profile.getId())
                .name(profile.getMember().getNickname())
                .schoolName(profile.getSchool().getName())
                .departmentName(profile.getDepartment().getName())
                .studentNo(profile.getStudentNo())
                .admissionYear(profile.getAdmissionYear())
                .gradeLevel(profile.getCurrentGrade())
                .semester(profile.getCurrentTerm())
                .enrollmentStatus(profile.getEnrollmentStatus())
                .majors(majorInfos)
                .build();
    }

    @Transactional(readOnly = true)
    public StudentCourseListResponse getMyCourses(Long memberId) {
        StudentProfile profile = studentProfileRepository.findWithDetailsByMemberId(memberId)
                .orElseThrow(() -> new BaseException(ErrorCode.STUDENT_PROFILE_NOT_FOUND));

        List<StudentCourse> courses = studentCourseRepository.findWithCourseByStudentProfile(profile);

        List<StudentCourseListResponse.CourseInfo> courseInfos = courses.stream()
                .map(this::toCourseInfo)
                .toList();

        return StudentCourseListResponse.builder()
                .courses(courseInfos)
                .build();
    }

    // 검수 화면([저장하기])에서 넘어온 전체 목록으로 STUDENT_COURSE를 완전히 교체한다.
    // studentCourseId가 있으면 수정, 없으면 신규(직접추가), 요청에 없는 기존 항목은 삭제.
    @Transactional
    public void updateMyCourses(Long memberId, StudentCourseUpdateRequest request) {
        StudentProfile profile = studentProfileRepository.findWithDetailsByMemberId(memberId)
                .orElseThrow(() -> new BaseException(ErrorCode.STUDENT_PROFILE_NOT_FOUND));

        Map<Long, StudentCourse> existingById = studentCourseRepository.findByStudentProfile(profile).stream()
                .collect(Collectors.toMap(StudentCourse::getId, sc -> sc));

        Set<Long> keepIds = new HashSet<>();
        List<StudentCourse> newCourses = new ArrayList<>();

        for (CourseUpdateItem item : request.courses()) {
            Course course = findCourseOrThrow(item.courseId());
            Department department = findDepartmentOrThrow(item.departmentId());
            Division division = findDivisionOrThrow(item.appliedDivisionId());

            if (item.studentCourseId() == null) {
                newCourses.add(StudentCourse.builder()
                        .studentProfile(profile)
                        .course(course)
                        .appliedDepartment(department)
                        .appliedDivision(division)
                        .rawCourseCode(course != null ? course.getCourseCode() : null)
                        .rawCourseName(item.rawCourseName())
                        .credit(item.credit())
                        .takenYear(item.takenYear())
                        .takenSemester(item.takenSemester())
                        .isRetake(false)
                        .status(CourseStatus.COMPLETED)
                        .source(RecordSource.MANUAL)
                        .build());
                continue;
            }

            // 다른 학생의 studentCourseId를 보내거나 존재하지 않는 id면 잘못된 입력값으로 취급한다.
            StudentCourse existing = existingById.get(item.studentCourseId());
            if (existing == null) {
                throw new BaseException(ErrorCode.INVALID_INPUT_VALUE);
            }
            existing.applyEdit(course, item.rawCourseName(), department, item.credit(), division,
                    item.takenYear(), item.takenSemester());
            keepIds.add(existing.getId());
        }

        List<StudentCourse> toDelete = existingById.values().stream()
                .filter(sc -> !keepIds.contains(sc.getId()))
                .toList();
        studentCourseRepository.deleteAll(toDelete);
        studentCourseRepository.saveAll(newCourses);
    }

    private Course findCourseOrThrow(Long courseId) {
        if (courseId == null) {
            return null;
        }
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new BaseException(ErrorCode.INVALID_INPUT_VALUE));
    }

    private Department findDepartmentOrThrow(Long departmentId) {
        if (departmentId == null) {
            return null;
        }
        return departmentRepository.findById(departmentId)
                .orElseThrow(() -> new BaseException(ErrorCode.INVALID_INPUT_VALUE));
    }

    private Division findDivisionOrThrow(Long divisionId) {
        if (divisionId == null) {
            return null;
        }
        return divisionRepository.findById(divisionId)
                .orElseThrow(() -> new BaseException(ErrorCode.INVALID_INPUT_VALUE));
    }

    private StudentCourseListResponse.CourseInfo toCourseInfo(StudentCourse course) {
        return StudentCourseListResponse.CourseInfo.builder()
                .studentCourseId(course.getId())
                .courseCode(course.getRawCourseCode())
                .name(course.getRawCourseName())
                .departmentName(departmentName(course))
                .credit(course.getCredit())
                .appliedDivisionName(appliedDivisionName(course))
                .takenYear(course.getTakenYear())
                .takenSemester(takenSemesterName(course.getTakenSemester()))
                .build();
    }

    private String takenSemesterName(Semester takenSemester) {
        if (takenSemester == null) {
            return null;
        }
        return switch (takenSemester) {
            case FIRST -> "1학기";
            case SECOND -> "2학기";
            case SUMMER -> "여름학기";
            case WINTER -> "겨울학기";
        };
    }

    // 사용자가 검수 화면에서 개설학부를 직접 바꿨으면(appliedDepartment) 그 값을 우선한다.
    // 아니면 COURSE 매칭 결과를 쓰고, 그마저 없으면 교양 과목일 때만 "교양"으로 표시한다.
    private String departmentName(StudentCourse course) {
        if (course.getAppliedDepartment() != null) {
            return course.getAppliedDepartment().getName();
        }
        if (course.getCourse() != null && course.getCourse().getOfferingDepartment() != null) {
            return course.getCourse().getOfferingDepartment().getName();
        }
        return isGeneralEducation(course) ? "교양" : null;
    }

    // appliedDivision의 category가 교양 계열(4개) 중 하나면 교양 과목으로 본다.
    private boolean isGeneralEducation(StudentCourse course) {
        if (course.getAppliedDivision() == null) {
            return false;
        }
        return switch (course.getAppliedDivision().getCategory()) {
            case GE_REQUIRED, GE_DISTRIBUTION, GE_FREE, GENERAL_ELECTIVE -> true;
            case MAJOR_BASIC, MAJOR_REQUIRED, MAJOR_ELECTIVE -> false;
        };
    }

    private String appliedDivisionName(StudentCourse course) {
        return course.getAppliedDivision() == null ? null : divisionCategoryName(course.getAppliedDivision().getCategory());
    }

    private String divisionCategoryName(DivisionCategory category) {
        return switch (category) {
            case MAJOR_BASIC -> "전공기초";
            case MAJOR_REQUIRED -> "전공필수";
            case MAJOR_ELECTIVE -> "전공선택";
            case GE_REQUIRED -> "필수교과";
            case GE_DISTRIBUTION -> "배분이수";
            case GE_FREE -> "자유이수";
            case GENERAL_ELECTIVE -> "일반선택";
        };
    }
}