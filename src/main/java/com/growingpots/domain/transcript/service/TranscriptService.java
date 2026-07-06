package com.growingpots.domain.transcript.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growingpots.domain.transcript.parser.ParsedTranscript;
import com.growingpots.domain.transcript.parser.PdfParsingException;
import com.growingpots.domain.transcript.parser.PdfTranscriptParser;
import com.growingpots.domain.user.entity.Member;
import com.growingpots.domain.user.entity.StudentProfile;
import com.growingpots.domain.user.repository.MemberRepository;
import com.growingpots.domain.user.repository.StudentProfileRepository;
import com.growingpots.global.exception.BaseException;
import com.growingpots.global.response.error.ErrorCode;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

// PDF 파싱(DB 무관)과 DB 저장(TranscriptPersister)을 분리했다. 파싱하는 동안 DB 커넥션을 붙들고 있지 않기 위함.
@Slf4j
@Service
@RequiredArgsConstructor
public class TranscriptService {

    private static final byte[] PDF_MAGIC_BYTES = {'%', 'P', 'D', 'F'};

    private final MemberRepository memberRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final PdfTranscriptParser pdfTranscriptParser;
    private final TranscriptPersister transcriptPersister;
    private final ObjectMapper objectMapper;

    public void uploadTranscript(Long memberId, MultipartFile file) {
        StudentProfile studentProfile = findStudentProfile(memberId);

        byte[] pdfBytes = readBytes(file);
        validatePdfFormat(pdfBytes);

        ParsedTranscript parsed;
        try {
            parsed = pdfTranscriptParser.parse(pdfBytes);
        } catch (PdfParsingException e) {
            throw new BaseException(ErrorCode.PDF_PARSING_FAILED, e.getMessage());
        }
        validateNotEmpty(parsed);
        logForVerification(parsed);

        transcriptPersister.persist(studentProfile, parsed);
    }

    private StudentProfile findStudentProfile(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));
        return studentProfileRepository.findByMember(member)
                .orElseThrow(() -> new BaseException(ErrorCode.STUDENT_PROFILE_NOT_FOUND));
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BaseException(ErrorCode.PDF_PARSING_FAILED, e.getMessage());
        }
    }

    private void validatePdfFormat(byte[] pdfBytes) {
        if (pdfBytes.length < PDF_MAGIC_BYTES.length) {
            throw new BaseException(ErrorCode.PDF_INVALID_FORMAT);
        }
        for (int i = 0; i < PDF_MAGIC_BYTES.length; i++) {
            if (pdfBytes[i] != PDF_MAGIC_BYTES[i]) {
                throw new BaseException(ErrorCode.PDF_INVALID_FORMAT);
            }
        }
    }

    // 과목/전공요건이 둘 다 비어있으면 파싱 자체는 성공해도 사실상 실패한 것이므로 여기서 걸러낸다.
    private void validateNotEmpty(ParsedTranscript parsed) {
        if (parsed.courses().isEmpty() && parsed.majorRequirements().isEmpty()) {
            throw new BaseException(ErrorCode.PDF_PARSING_FAILED, "파싱된 과목/전공 정보가 없습니다.");
        }
    }

    // 자동 파싱 결과를 눈으로 검증하기 위한 용도 (DB에는 courses만 저장)
    private void logForVerification(ParsedTranscript parsed) {
        try {
            log.debug("졸업사정표 파싱 결과: {}", objectMapper.writeValueAsString(parsed));
        } catch (JsonProcessingException e) {
            log.warn("파싱 결과 로깅 실패: {}", e.getMessage());
        }
    }
}
