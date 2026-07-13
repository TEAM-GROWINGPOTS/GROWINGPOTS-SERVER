package com.growingpots.domain.planner.controller;

import com.growingpots.domain.graduation.dto.response.GraduationResponse;
import com.growingpots.domain.graduation.enums.GraduationSource;
import com.growingpots.domain.graduation.enums.MajorTypeFilter;
import com.growingpots.domain.graduation.service.GraduationService;
import com.growingpots.domain.planner.dto.request.PlannerSaveRequest;
import com.growingpots.domain.planner.dto.request.PrerequisiteCheckRequest;
import com.growingpots.domain.planner.dto.request.SelectVersionRequest;
import com.growingpots.domain.planner.dto.response.PlannerResponse;
import com.growingpots.domain.planner.dto.response.PrerequisiteCheckResponse;
import com.growingpots.domain.planner.dto.response.SelectVersionResponse;
import com.growingpots.domain.planner.service.PlannerService;
import com.growingpots.global.exception.BaseException;
import com.growingpots.global.response.BaseResponse;
import com.growingpots.global.response.error.ErrorCode;
import com.growingpots.global.response.error.ErrorType;
import com.growingpots.global.response.success.SuccessCode;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@PlannerApi
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/planner")
public class PlannerController {

    private final PlannerService plannerService;
    private final GraduationService graduationService;

    @PlannerApi.GetPlanner
    @GetMapping
    public ResponseEntity<BaseResponse<PlannerResponse>> getPlanner(Authentication authentication) {
        Long memberId = Long.parseLong(authentication.getName());
        PlannerResponse response = plannerService.getPlanner(memberId);
        return ResponseEntity.ok(BaseResponse.success(SuccessCode.PLANNER_FOUND, response));
    }

    @PlannerApi.SavePlanner
    @PutMapping
    public ResponseEntity<BaseResponse<GraduationResponse>> savePlanner(
            @Valid @RequestBody PlannerSaveRequest request,
            Authentication authentication
    ) {
        Long memberId = Long.parseLong(authentication.getName());
        try {
            plannerService.savePlanner(memberId, request);
            GraduationResponse graduation = computeGraduationQuietly(memberId);
            return ResponseEntity.ok(BaseResponse.success(SuccessCode.PLANNER_SAVED, graduation));
        } catch (BaseException e) {
            ErrorType errorType = e.getErrorType();
            // 인증·프로필 오류는 이전 상태를 계산할 수 없거나 데이터 누출 위험이 있으므로 GlobalExceptionHandler로 위임
            if (errorType == ErrorCode.STUDENT_PROFILE_NOT_FOUND || errorType == ErrorCode.PLANNER_ACCESS_DENIED) {
                throw e;
            }
            GraduationResponse previousState = computeGraduationQuietly(memberId);
            return ResponseEntity
                    .status(errorType.getStatus())
                    .body(BaseResponse.error(errorType, previousState));
        }
    }

    // getGraduation 실패 시 null 폴백 — 저장 성공/실패 여부와 무관하게 졸업현황 계산 오류가
    // 전체 응답을 망가뜨리지 않도록 한다. (테스트 환경처럼 StudentMajor가 없는 경우 포함)
    private GraduationResponse computeGraduationQuietly(Long memberId) {
        try {
            return graduationService.getGraduation(memberId, MajorTypeFilter.ALL, null, GraduationSource.PLANNED);
        } catch (Exception e) {
            log.warn("[PlannerController] 졸업현황 계산 실패 (memberId={}): {}", memberId, e.getMessage());
            return null;
        }
    }

    @PlannerApi.CheckPrerequisites
    @PostMapping("/prerequisite-check")
    public ResponseEntity<BaseResponse<PrerequisiteCheckResponse>> checkPrerequisites(
            @Valid @RequestBody PrerequisiteCheckRequest request,
            Authentication authentication
    ) {
        Long memberId = Long.parseLong(authentication.getName());
        PrerequisiteCheckResponse response = plannerService.checkPrerequisites(memberId, request);
        return ResponseEntity.ok(BaseResponse.success(SuccessCode.PREREQUISITE_CHECK_DONE, response));
    }

    @PlannerApi.SelectVersion
    @PatchMapping("/terms/{plannerTermId}/selected-version")
    public ResponseEntity<BaseResponse<SelectVersionResponse>> selectVersion(
            @Parameter(description = "선택 버전을 변경할 플래너 학기 PK")
            @PathVariable Long plannerTermId,
            @Valid @RequestBody SelectVersionRequest request,
            Authentication authentication
    ) {
        Long memberId = Long.parseLong(authentication.getName());
        SelectVersionResponse response = plannerService.selectVersion(memberId, plannerTermId, request);
        return ResponseEntity.ok(BaseResponse.success(SuccessCode.PLANNER_VERSION_SELECTED, response));
    }
}