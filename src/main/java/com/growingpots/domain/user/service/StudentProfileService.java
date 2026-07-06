package com.growingpots.domain.user.service;

import com.growingpots.domain.transcript.entity.StudentCourse;
import com.growingpots.domain.transcript.repository.StudentCourseRepository;
import com.growingpots.domain.university.entity.Department;
import com.growingpots.domain.university.entity.School;
import com.growingpots.domain.university.repository.DepartmentRepository;
import com.growingpots.domain.university.repository.SchoolRepository;
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

import java.util.Set;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StudentProfileService {

    // 교양 이수구분(section)만 그대로 표시용으로 재사용한다. 전공 과목은 section에 전공/트랙명이 들어있어 여기 해당 안 됨.
    private static final Set<String> GENERAL_EDUCATION_SECTIONS = Set.of("필수교과", "배분이수", "자유이수", "기타");

    private final MemberRepository memberRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final StudentMajorRepository studentMajorRepository;
    private final StudentCourseRepository studentCourseRepository;
    private final SchoolRepository schoolRepository;
    private final DepartmentRepository departmentRepository;

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
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));
        StudentProfile profile = studentProfileRepository.findByMember(member)
                .orElseThrow(() -> new BaseException(ErrorCode.STUDENT_PROFILE_NOT_FOUND));

        List<StudentCourse> courses = studentCourseRepository.findWithCourseByStudentProfile(profile);

        List<StudentCourseListResponse.CourseInfo> courseInfos = courses.stream()
                .map(this::toCourseInfo)
                .toList();

        return StudentCourseListResponse.builder()
                .courses(courseInfos)
                .build();
    }

    private StudentCourseListResponse.CourseInfo toCourseInfo(StudentCourse course) {
        return StudentCourseListResponse.CourseInfo.builder()
                .studentCourseId(course.getId())
                .courseCode(course.getRawCourseCode())
                .name(course.getRawCourseName())
                .departmentName(course.getCourse() != null && course.getCourse().getOfferingDepartment() != null
                        ? course.getCourse().getOfferingDepartment().getName() : null)
                .credit(course.getCredit())
                .appliedDivisionName(GENERAL_EDUCATION_SECTIONS.contains(course.getSection()) ? course.getSection() : null)
                .takenYear(course.getTakenYear())
                .takenSemester(course.getTakenSemester() == null ? null : course.getTakenSemester() + "학기")
                .build();
    }
}