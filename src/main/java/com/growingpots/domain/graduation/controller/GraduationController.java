package com.growingpots.domain.graduation.controller;

import com.growingpots.domain.graduation.dto.response.GraduationCourseResponse;
import com.growingpots.domain.graduation.dto.response.GraduationResponse;
import com.growingpots.domain.graduation.enums.GraduationSource;
import com.growingpots.domain.graduation.enums.MajorTypeFilter;
import com.growingpots.domain.graduation.service.GraduationService;
import com.growingpots.global.response.BaseResponse;
import com.growingpots.global.response.success.SuccessCode;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@GraduationApi
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/students/me/graduation")
public class GraduationController {

    private final GraduationService graduationService;

    @GraduationApi.GetGraduationStatus
    @GetMapping
    public ResponseEntity<BaseResponse<GraduationResponse>> getGraduation(
            @Parameter(description = """
                    전공 필터. department 파라미터가 있으면 무시됨. 기본값: ALL
                    - ALL: 보유 전공 전부(sections.majors) + 교양 + 기타 (4섹션 이상 분리)
                    - GE: 교양 (REQUIRED_GE·DISTRIBUTED_GE·FREE_GE + 영어·SW)
                    - OTHERS: 기타 (GENERAL_ELECTIVE만, 영어·SW 미포함)""")
            @RequestParam(defaultValue = "ALL") MajorTypeFilter majorType,

            @Parameter(description = """
                    특정 전공 하나만 조회할 학과명(예: 경영학과). 있으면 majorType은 무시하고 그 전공
                    하나의 단건 응답(conditions/graduationRequired 채움, sections=null)을 반환한다.
                    본전공이든 복수전공이든 구분 없이 학생이 등록한 학과명으로 조회한다. 학생의 전공에
                    없는 학과명이면 404.""")
            @RequestParam(required = false) String department,

            @Parameter(description = """
                    조회 기준. 기본값: COMPLETED
                    - COMPLETED: 이수 완료 기준 (PDF 스냅샷)
                    - PLANNED: 플래너의 계획 과목을 스냅샷에 합산한 예상 졸업현황. 플래너가 없거나 신규 계획 과목이 없으면 COMPLETED와 동일""")
            @RequestParam(defaultValue = "COMPLETED") GraduationSource source,

            Authentication authentication
    ) {
        Long memberId = Long.parseLong(authentication.getName());
        GraduationResponse response = graduationService.getGraduation(memberId, majorType, department, source);
        return ResponseEntity.ok(BaseResponse.success(SuccessCode.GRADUATION_STATUS_FOUND, response));
    }

    @GraduationApi.GetDivisionCourses
    @GetMapping("/{divisionCode}/courses")
    public ResponseEntity<BaseResponse<GraduationCourseResponse>> getDivisionCourses(
            @Parameter(
                    description = """
                            이수구분 코드.
                            - 전공 탭: MAJOR_BASIC(전공 기초) · MAJOR_REQUIRED(전공 필수) · MAJOR_ELECTIVE(전공 선택)
                            - 교양 탭: REQUIRED_GE(필수 교과) · DISTRIBUTED_GE(배분 이수 교과) · FREE_GE(자유 이수 교과)
                            - 기타 탭: GENERAL_ELECTIVE
                            - 전공·교양 탭 공통: ENGLISH_COURSE(영어 강의) · SW_CERT_COURSE(SW 인증 강의)
                            - GRADUATION_REQUIRED: 학과 독립 졸업요건 (예: 스포츠의학과 졸업필수)""",
                    schema = @Schema(
                            type = "string",
                            allowableValues = {
                                    "MAJOR_BASIC", "MAJOR_REQUIRED", "MAJOR_ELECTIVE",
                                    "REQUIRED_GE", "DISTRIBUTED_GE", "FREE_GE",
                                    "GENERAL_ELECTIVE", "ENGLISH_COURSE", "SW_CERT_COURSE",
                                    "GRADUATION_REQUIRED"
                            }))
            @PathVariable String divisionCode,

            @Parameter(description = """
                    전공 필터. department 파라미터가 있으면 무시됨. 기본값: ALL
                    - ALL: 보유 전공 전부 / GE·OTHERS: 본전공 스냅샷 기준
                    - ENGLISH_COURSE·SW_CERT_COURSE + ALL: 탭·학과 구분 없이 전체 합산 (majors 1개, majorType=null)
                    - ENGLISH_COURSE·SW_CERT_COURSE + OTHERS: 빈 응답 (majors=[])""")
            @RequestParam(defaultValue = "ALL") MajorTypeFilter majorType,

            @Parameter(description = """
                    특정 전공 하나만 조회할 학과명(예: 경영학과). 있으면 majorType은 무시하고
                    그 전공 하나만 majors 배열에 담아 반환한다. 학생의 전공에 없는 학과명이면 404.""")
            @RequestParam(required = false) String department,

            Authentication authentication
    ) {
        Long memberId = Long.parseLong(authentication.getName());
        GraduationCourseResponse response =
                graduationService.getCoursesByDivision(memberId, divisionCode, majorType, department);
        return ResponseEntity.ok(BaseResponse.success(SuccessCode.GRADUATION_COURSE_FOUND, response));
    }
}