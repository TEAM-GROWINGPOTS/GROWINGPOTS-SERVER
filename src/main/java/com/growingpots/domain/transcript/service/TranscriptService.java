package com.growingpots.domain.transcript.service;

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
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
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
    // 파싱이 여러 개 겹치면 메모리가 같이 늘어 메모리 부족이 날 수 있어 동시에 도는 수를 제한한다(#292).
    // 정상 파일 파싱은 1초 안에 끝나서, 잠깐 기다려도 자리가 안 나면 거절한다.
    static final int MAX_CONCURRENT_PARSING = 2;
    static final long PARSING_WAIT_SECONDS = 3;

    private final MemberRepository memberRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final PdfTranscriptParser pdfTranscriptParser;
    private final TranscriptPersister transcriptPersister;
    private final Semaphore parsingPermits = new Semaphore(MAX_CONCURRENT_PARSING);

    public void uploadTranscript(Long memberId, MultipartFile file) {
        StudentProfile studentProfile = findStudentProfile(memberId);

        byte[] pdfBytes = readBytes(file);
        validatePdfFormat(pdfBytes);

        ParsedTranscript parsed = parse(pdfBytes);
        validateNotEmpty(parsed);
        logForVerification(parsed);

        transcriptPersister.persist(studentProfile, parsed);
    }

    private ParsedTranscript parse(byte[] pdfBytes) {
        acquireParsingPermit();
        try {
            return pdfTranscriptParser.parse(pdfBytes);
        } catch (PdfParsingException e) {
            throw new BaseException(ErrorCode.PDF_PARSING_FAILED, e.getMessage());
        } finally {
            parsingPermits.release();
        }
    }

    private void acquireParsingPermit() {
        try {
            if (!parsingPermits.tryAcquire(PARSING_WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new BaseException(ErrorCode.PDF_PARSING_BUSY);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BaseException(ErrorCode.PDF_PARSING_BUSY);
        }
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

    // 이름·학번 등 개인정보가 로그에 남지 않도록 건수만 기록한다.
    private void logForVerification(ParsedTranscript parsed) {
        log.debug("졸업사정표 파싱 결과: 과목 {}건, 전공 {}건", parsed.courses().size(), parsed.majorRequirements().size());
    }
}
