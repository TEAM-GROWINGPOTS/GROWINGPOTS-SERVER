package com.growingpots.domain.planner.controller;

import com.growingpots.domain.planner.dto.request.PlannerSaveRequest;
import com.growingpots.domain.planner.dto.request.PrerequisiteCheckRequest;
import com.growingpots.domain.planner.dto.request.SelectVersionRequest;
import com.growingpots.domain.planner.dto.response.PlannerResponse;
import com.growingpots.domain.planner.dto.response.PlannerSaveResponse;
import com.growingpots.domain.planner.dto.response.PrerequisiteCheckResponse;
import com.growingpots.domain.planner.dto.response.SelectVersionResponse;
import com.growingpots.domain.planner.service.PlannerService;
import com.growingpots.global.response.BaseResponse;
import com.growingpots.global.response.success.SuccessCode;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
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

@PlannerApi
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/planner")
public class PlannerController {

    private final PlannerService plannerService;

    @PlannerApi.GetPlanner
    @GetMapping
    public ResponseEntity<BaseResponse<PlannerResponse>> getPlanner(Authentication authentication) {
        Long memberId = Long.parseLong(authentication.getName());
        PlannerResponse response = plannerService.getPlanner(memberId);
        return ResponseEntity.ok(BaseResponse.success(SuccessCode.PLANNER_FOUND, response));
    }

    @PlannerApi.SavePlanner
    @PutMapping
    public ResponseEntity<BaseResponse<PlannerSaveResponse>> savePlanner(
            @Valid @RequestBody PlannerSaveRequest request,
            Authentication authentication
    ) {
        Long memberId = Long.parseLong(authentication.getName());
        PlannerSaveResponse response = plannerService.savePlanner(memberId, request);
        return ResponseEntity.ok(BaseResponse.success(SuccessCode.PLANNER_SAVED, response));
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