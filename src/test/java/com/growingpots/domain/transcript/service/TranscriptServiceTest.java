package com.growingpots.domain.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.growingpots.domain.transcript.parser.ParsedTranscript;
import com.growingpots.domain.transcript.parser.PdfTranscriptParser;
import com.growingpots.domain.user.entity.Member;
import com.growingpots.domain.user.entity.StudentProfile;
import com.growingpots.domain.user.repository.MemberRepository;
import com.growingpots.domain.user.repository.StudentProfileRepository;
import com.growingpots.global.exception.BaseException;
import com.growingpots.global.response.error.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class TranscriptServiceTest {

    private static final MockMultipartFile PDF =
            new MockMultipartFile("file", "transcript.pdf", "application/pdf", "%PDF-1.7".getBytes());
    private static final ParsedTranscript PARSED = new ParsedTranscript(
            Map.of(), Map.of(), List.of(), List.of(), List.of(Map.of("courseCode", "CS101")));

    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final StudentProfileRepository studentProfileRepository = mock(StudentProfileRepository.class);
    private final PdfTranscriptParser parser = mock(PdfTranscriptParser.class);
    private final TranscriptPersister persister = mock(TranscriptPersister.class);
    private final TranscriptService service =
            new TranscriptService(memberRepository, studentProfileRepository, parser, persister);
    private final ExecutorService executor = Executors.newFixedThreadPool(TranscriptService.MAX_CONCURRENT_PARSING);

    @BeforeEach
    void setUp() {
        Member member = mock(Member.class);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(studentProfileRepository.findByMember(member)).thenReturn(Optional.of(mock(StudentProfile.class)));
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void 동시_파싱_수가_가득_차면_기다렸다가_503으로_거절한다() throws Exception {
        CountDownLatch started = new CountDownLatch(TranscriptService.MAX_CONCURRENT_PARSING);
        CountDownLatch finish = new CountDownLatch(1);
        when(parser.parse(any())).thenAnswer(invocation -> {
            started.countDown();
            finish.await();
            return PARSED;
        });

        List<Future<?>> running = List.of(
                executor.submit(() -> service.uploadTranscript(1L, PDF)),
                executor.submit(() -> service.uploadTranscript(1L, PDF)));
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        long begin = System.nanoTime();
        assertThatThrownBy(() -> service.uploadTranscript(1L, PDF))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorType())
                .isEqualTo(ErrorCode.PDF_PARSING_BUSY);
        assertThat(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - begin))
                .isGreaterThanOrEqualTo(TranscriptService.PARSING_WAIT_SECONDS - 1);

        finish.countDown();
        for (Future<?> future : running) {
            future.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void 파싱이_끝나면_자리가_반환되어_다음_요청을_처리한다() {
        when(parser.parse(any())).thenReturn(PARSED);

        for (int i = 0; i < TranscriptService.MAX_CONCURRENT_PARSING + 1; i++) {
            service.uploadTranscript(1L, PDF);
        }
    }

    @Test
    void 파싱이_실패해도_자리가_반환된다() {
        when(parser.parse(any())).thenThrow(new RuntimeException("boom"));

        for (int i = 0; i < TranscriptService.MAX_CONCURRENT_PARSING + 1; i++) {
            assertThatThrownBy(() -> service.uploadTranscript(1L, PDF)).hasMessage("boom");
        }
        doReturn(PARSED).when(parser).parse(any());
        service.uploadTranscript(1L, PDF);
    }
}
