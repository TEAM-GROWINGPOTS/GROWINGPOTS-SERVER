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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudentProfileService {

    // MVP라 경희대(국제캠퍼스) 하나만 하드코딩. 교양 과목인데 매칭되는 개설학과가 없으면
    // 이 이름의 Department가 그 학생 학교에 있는지 찾아 대체로 쓴다(없으면 기존처럼 "교양" 표시값).
    private static final String GENERAL_EDUCATION_FALLBACK_DEPARTMENT_NAME = "후마니타스칼리지(국제)";

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
                .enrollmentStatus(profile.getEnrollmentStatus() != null ? profile.getEnrollmentStatus() + " 중" : null)
                .majors(majorInfos)
                .build();
    }

    @Transactional(readOnly = true)
    public StudentCourseListResponse getMyCourses(Long memberId) {
        StudentProfile profile = studentProfileRepository.findWithDetailsByMemberId(memberId)
                .orElseThrow(() -> new BaseException(ErrorCode.STUDENT_PROFILE_NOT_FOUND));

        List<StudentCourse> courses = studentCourseRepository.findWithCourseByStudentProfile(profile);

        // 매칭되는 개설학과가 없는 교양 과목에 쓸 학교별 대체 학과. 없으면 "교양" 표시값으로 폴백한다.
        Department geFallbackDepartment = departmentRepository
                .findBySchoolIdAndName(profile.getSchool().getId(), GENERAL_EDUCATION_FALLBACK_DEPARTMENT_NAME)
                .orElse(null);

        List<StudentCourseListResponse.CourseInfo> courseInfos = courses.stream()
                .map(course -> toCourseInfo(course, geFallbackDepartment))
                .toList();

        List<Division> divisions = divisionRepository.findBySchool(profile.getSchool());
        List<StudentCourseListResponse.DivisionInfo> divisionInfos = divisions.stream()
                .sorted(Comparator.comparingInt(d -> d.getCategory().ordinal()))
                .map(d -> StudentCourseListResponse.DivisionInfo.builder()
                        .id(d.getId())
                        .name(d.getCategory().getDisplayName())
                        .build())
                .toList();

        return StudentCourseListResponse.builder()
                .courses(courseInfos)
                .availableDivisions(divisionInfos)
                .build();
    }

    // 검수 화면([저장하기])에서 넘어온 전체 목록으로 STUDENT_COURSE를 완전히 교체한다.
    // studentCourseId가 있으면 수정, 없으면 신규(직접추가), 요청에 없는 기존 항목은 삭제.
    @Transactional
    public void updateMyCourses(Long memberId, StudentCourseUpdateRequest request) {
        StudentProfile profile = studentProfileRepository.findWithDetailsByMemberId(memberId)
                .orElseThrow(() -> new BaseException(ErrorCode.STUDENT_PROFILE_NOT_FOUND));
        School school = profile.getSchool();
        List<CourseUpdateItem> items = request.courses();

        // 항목마다 개별 조회하지 않도록 요청에 나온 id를 모아 한 번씩만 조회하고, 학생의 학교 소속이 아닌 건
        // 걸러낸다(다른 학교의 course/department/division id가 섞여 들어오는 것 방지).
        Map<Long, Course> coursesById = courseRepository.findAllById(ids(items, CourseUpdateItem::courseId)).stream()
                .filter(course -> course.getSchool().getId().equals(school.getId()))
                .collect(Collectors.toMap(Course::getId, c -> c));
        Map<Long, Department> departmentsById = departmentRepository.findAllById(ids(items, CourseUpdateItem::departmentId)).stream()
                .filter(department -> department.getSchool().getId().equals(school.getId()))
                .collect(Collectors.toMap(Department::getId, d -> d));
        Map<Long, Division> divisionsById = divisionRepository.findAllById(ids(items, CourseUpdateItem::appliedDivisionId)).stream()
                .filter(division -> division.getSchool().getId().equals(school.getId()))
                .collect(Collectors.toMap(Division::getId, d -> d));

        Map<Long, StudentCourse> existingById = studentCourseRepository.findByStudentProfile(profile).stream()
                .collect(Collectors.toMap(StudentCourse::getId, sc -> sc));

        Set<Long> keepIds = new HashSet<>();
        List<StudentCourse> newCourses = new ArrayList<>();

        for (CourseUpdateItem item : items) {
            Course course = requireInMap(coursesById, item.courseId());
            Department department = requireInMap(departmentsById, item.departmentId());
            Division division = requireInMap(divisionsById, item.appliedDivisionId());

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
                    item.takenYear() != null ? item.takenYear() : existing.getTakenYear(),
                    item.takenSemester() != null ? item.takenSemester() : existing.getTakenSemester());
            keepIds.add(existing.getId());
        }

        List<StudentCourse> toDelete = existingById.values().stream()
                .filter(sc -> !keepIds.contains(sc.getId()))
                .toList();
        studentCourseRepository.deleteAllInBatch(toDelete);
        studentCourseRepository.saveAll(newCourses);
    }

    private Set<Long> ids(List<CourseUpdateItem> items, Function<CourseUpdateItem, Long> extractor) {
        return items.stream().map(extractor).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    // 요청에 id가 있는데 (다른 학교 소속이라 걸러졌거나 존재하지 않아) 못 찾았으면 잘못된 입력값으로 취급한다.
    private <T> T requireInMap(Map<Long, T> map, Long id) {
        if (id == null) {
            return null;
        }
        T value = map.get(id);
        if (value == null) {
            throw new BaseException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return value;
    }

    private StudentCourseListResponse.CourseInfo toCourseInfo(StudentCourse course, Department geFallbackDepartment) {
        String departmentName = departmentName(course, geFallbackDepartment);
        // 개설학부를 알 수 없으면(departmentName=null, 프론트에서 "해당없음") 이수영역도 함께 비운다 -
        // 학부는 "해당없음"인데 이수영역만 채워져 검수 화면에 뜨는 게 혼란스럽다는 프론트 요청(#195).
        // 교양 과목은 departmentName이 항상 "교양"(또는 대체 학과명)으로 채워지므로 영향 없다.
        boolean departmentUnknown = departmentName == null;
        return StudentCourseListResponse.CourseInfo.builder()
                .studentCourseId(course.getId())
                .courseCode(course.getRawCourseCode())
                .name(course.getRawCourseName())
                .departmentName(departmentName)
                .departmentId(departmentId(course, geFallbackDepartment))
                .credit(course.getCredit())
                .appliedDivisionName(departmentUnknown ? null : appliedDivisionName(course))
                .appliedDivisionId(departmentUnknown || course.getAppliedDivision() == null
                        ? null : course.getAppliedDivision().getId())
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
    // 아니면 COURSE 매칭 결과를 쓰고, 그마저 없으면 교양 과목일 때만 학교의 대체 학과(geFallbackDepartment,
    // 없으면 "교양" 표시값)로 폴백한다.
    private String departmentName(StudentCourse course, Department geFallbackDepartment) {
        if (course.getAppliedDepartment() != null) {
            return course.getAppliedDepartment().getName();
        }
        if (course.getCourse() != null && course.getCourse().getOfferingDepartment() != null) {
            return course.getCourse().getOfferingDepartment().getName();
        }
        if (!isGeneralEducation(course)) {
            return null;
        }
        return geFallbackDepartment != null ? geFallbackDepartment.getName() : "교양";
    }

    // departmentName()과 같은 우선순위로 실제 PK를 돌려준다. 학교에 대체 학과가 지정돼 있지 않아
    // "교양"이라는 표시용 문자열로 폴백한 경우에만 대응하는 ID가 없다 - 이 경우 null.
    private Long departmentId(StudentCourse course, Department geFallbackDepartment) {
        if (course.getAppliedDepartment() != null) {
            return course.getAppliedDepartment().getId();
        }
        if (course.getCourse() != null && course.getCourse().getOfferingDepartment() != null) {
            return course.getCourse().getOfferingDepartment().getId();
        }
        if (isGeneralEducation(course) && geFallbackDepartment != null) {
            return geFallbackDepartment.getId();
        }
        return null;
    }

    // appliedDivision의 category가 교양 계열(4개) 중 하나면 교양 과목으로 본다.
    private boolean isGeneralEducation(StudentCourse course) {
        if (course.getAppliedDivision() == null) {
            return false;
        }
        return switch (course.getAppliedDivision().getCategory()) {
            case REQUIRED_GE, DISTRIBUTED_GE, FREE_GE, GENERAL_ELECTIVE -> true;
            case MAJOR_BASIC, MAJOR_REQUIRED, MAJOR_ELECTIVE -> false;
        };
    }

    private String appliedDivisionName(StudentCourse course) {
        return course.getAppliedDivision() == null ? null : course.getAppliedDivision().getCategory().getDisplayName();
    }

}