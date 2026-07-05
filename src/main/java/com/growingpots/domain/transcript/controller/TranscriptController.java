package com.growingpots.domain.transcript.controller;

import com.growingpots.domain.transcript.service.TranscriptService;
import com.growingpots.global.response.BaseResponse;
import com.growingpots.global.response.success.SuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/diagnosis")
public class TranscriptController {

    private final TranscriptService transcriptService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BaseResponse<Void>> uploadTranscript(
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        Long memberId = Long.valueOf(authentication.getName());
        transcriptService.uploadTranscript(memberId, file);
        return ResponseEntity.status(SuccessCode.PDF_PARSED.getStatus())
                .body(BaseResponse.success(SuccessCode.PDF_PARSED));
    }
}
