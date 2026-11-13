package com.growingpots.global.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.growingpots.domain.transcript.parser.ParsedTranscript;
import com.growingpots.domain.transcript.parser.PdfTranscriptParser;
import com.growingpots.domain.transcript.service.TranscriptService;
import com.growingpots.domain.user.dto.request.StudentCourseUpdateRequest;
import com.growingpots.domain.user.dto.request.StudentCourseUpdateRequest.CourseUpdateItem;
import com.growingpots.domain.user.service.StudentProfileService;
import com.growingpots.global.discord.DiscordNotifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// 같은 학생 요청 2개가 동시에 처리될 때 데이터가 깨지거나 500이 나지 않는지 확인한다(#304, #305).
// H2는 MySQL과 잠금 동작이 달라서 재현이 안 돼, MigrationVerificationTest처럼 실제 MySQL에서만 돈다.
// MIGRATION_TEST_DB_URL이 있을 때만 실행된다. CI의 migration 잡이 MySQL을 띄워 이 값을 넣어 준다.
@EnabledIfEnvironmentVariable(named = "MIGRATION_TEST_DB_URL", matches = ".+")
@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.datasource.url=${MIGRATION_TEST_DB_URL}",
        "spring.datasource.username=${MIGRATION_TEST_DB_USERNAME:root}",
        "spring.datasource.password=${MIGRATION_TEST_DB_PASSWORD:}",
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class SameStudentConcurrentRequestTest {

    // 잠금이 없던 코드에서는 5번 모두 깨졌다. 타이밍에 따라 가끔만 깨지는 걸 놓치지 않으려고 여러 번 돌린다.
    private static final int ROUNDS = 5;
    private static final int COURSE_COUNT = 10;
    private static final String SCHOOL_NAME = "동시성테스트대";

    @Autowired
    private TranscriptService transcriptService;
    @Autowired
    private StudentProfileService studentProfileService;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoBean
    private PdfTranscriptParser parser;
    @MockitoBean
    private DiscordNotifier discordNotifier;

    // 테스트마다 파서가 언제 결과를 돌려줄지(바로/상대 요청과 맞춰서/신호를 받고) 바꿔 끼운다.
    private final AtomicReference<Supplier<ParsedTranscript>> parseBehavior = new AtomicReference<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private long schoolId;
    private long departmentId;

    @BeforeEach
    void setUp() {
        when(parser.parse(any())).thenAnswer(invocation -> parseBehavior.get().get());
        parseBehavior.set(SameStudentConcurrentRequestTest::parsed);
        seedSchoolOnce();
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void 검수_저장을_연달아_보내도_직접_추가한_과목은_한_번만_저장된다() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            long memberId = newStudentWithUploadedTranscript();
            StudentCourseUpdateRequest request = currentCoursesPlusOneManual(memberId);

            List<Future<?>> results = runAtOnce(
                    () -> studentProfileService.updateMyCourses(memberId, request),
                    () -> studentProfileService.updateMyCourses(memberId, request));

            assertAllSucceeded(results);
            assertThat(courseCount(memberId)).isEqualTo(COURSE_COUNT + 1);
            assertThat(manualCourseCount(memberId)).isEqualTo(1);
        }
    }

    @Test
    void PDF_재업로드_중에_온보딩을_확인해도_확인_기록이_남는다() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            long memberId = newStudentWithUploadedTranscript();
            CountDownLatch parsing = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            parseBehavior.set(() -> {
                parsing.countDown();
                await(release);
                return parsed();
            });

            Future<?> upload = executor.submit(() -> transcriptService.uploadTranscript(memberId, pdf()));
            assertThat(parsing.await(10, TimeUnit.SECONDS)).isTrue();
            studentProfileService.confirmOnboarding(memberId);
            release.countDown();
            upload.get(30, TimeUnit.SECONDS);

            assertThat(onboardingConfirmedAt(memberId)).isNotNull();
        }
    }

    @Test
    void 첫_PDF_업로드를_연달아_보내도_둘_다_성공하고_데이터가_한_벌만_남는다() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            long memberId = newStudent();
            assertUploadTwiceAtOnceSucceeds(memberId);
        }
    }

    @Test
    void PDF_재업로드를_연달아_보내도_둘_다_성공하고_데이터가_한_벌만_남는다() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            long memberId = newStudentWithUploadedTranscript();
            assertUploadTwiceAtOnceSucceeds(memberId);
        }
    }

    // 두 요청이 파싱을 끝내고 저장 단계에 동시에 들어가도록 맞춘다.
    private void assertUploadTwiceAtOnceSucceeds(long memberId) throws Exception {
        CyclicBarrier bothParsed = new CyclicBarrier(2);
        parseBehavior.set(() -> {
            await(bothParsed);
            return parsed();
        });

        List<Future<?>> results = runAtOnce(
                () -> transcriptService.uploadTranscript(memberId, pdf()),
                () -> transcriptService.uploadTranscript(memberId, pdf()));

        assertAllSucceeded(results);
        assertThat(courseCount(memberId)).isEqualTo(COURSE_COUNT);
        assertThat(mainMajorCount(memberId)).isEqualTo(1);
    }

    private List<Future<?>> runAtOnce(Runnable first, Runnable second) {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> results = List.of(
                executor.submit(() -> {
                    await(start);
                    first.run();
                }),
                executor.submit(() -> {
                    await(start);
                    second.run();
                }));
        start.countDown();
        return results;
    }

    // 실패한 요청이 있으면 get()이 그 예외를 그대로 던져 테스트가 실패한다.
    private void assertAllSucceeded(List<Future<?>> results) throws Exception {
        for (Future<?> result : results) {
            result.get(30, TimeUnit.SECONDS);
        }
    }

    private void seedSchoolOnce() {
        Long existing = jdbc.query("select id from school where name = ?",
                rs -> rs.next() ? rs.getLong(1) : null, SCHOOL_NAME);
        if (existing != null) {
            schoolId = existing;
            departmentId = jdbc.queryForObject("select id from department where school_id = ?", Long.class, schoolId);
            return;
        }
        jdbc.update("insert into school(created_at, updated_at, name) values(now(), now(), ?)", SCHOOL_NAME);
        schoolId = jdbc.queryForObject("select id from school where name = ?", Long.class, SCHOOL_NAME);
        jdbc.update("insert into department(created_at, updated_at, college, name, school_id) "
                + "values(now(), now(), '테스트대학', '테스트학과', ?)", schoolId);
        departmentId = jdbc.queryForObject("select id from department where school_id = ?", Long.class, schoolId);
        jdbc.update("insert into division(created_at, updated_at, category, code, school_id) "
                + "values(now(), now(), 'MAJOR_ELECTIVE', '05', ?)", schoolId);
        Long divisionId = jdbc.queryForObject("select id from division where school_id = ?", Long.class, schoolId);
        for (int i = 0; i < COURSE_COUNT; i++) {
            jdbc.update("insert into course(created_at, updated_at, course_code, credit, is_active, is_english, is_sw, "
                            + "name, default_division_id, offering_department_id, school_id) "
                            + "values(now(), now(), ?, 3, 1, 0, 0, ?, ?, ?, ?)",
                    "CONC" + i, "테스트과목" + i, divisionId, departmentId, schoolId);
        }
    }

    // 기본정보 입력까지 끝낸 학생(프로필 + 본전공 1개).
    private long newStudent() {
        String oauthId = "concurrency-" + System.nanoTime();
        jdbc.update("insert into member(created_at, updated_at, nickname, oauth_id, oauth_provider, role) "
                + "values(now(), now(), 'tester', ?, 'KAKAO', 'USER')", oauthId);
        long memberId = jdbc.queryForObject("select id from member where oauth_id = ?", Long.class, oauthId);
        jdbc.update("insert into student_profile(created_at, updated_at, admission_year, department_id, member_id, school_id) "
                + "values(now(), now(), 2023, ?, ?, ?)", departmentId, memberId, schoolId);
        jdbc.update("insert into student_major(created_at, updated_at, major_type, department_id, student_profile_id) "
                + "values(now(), now(), 'MAIN', ?, ?)", departmentId, profileId(memberId));
        return memberId;
    }

    private long newStudentWithUploadedTranscript() {
        long memberId = newStudent();
        // 이전 라운드에서 바꿔 끼운 파서 동작이 남아 있으면 준비용 업로드가 멈춰서, 바로 결과를 돌려주게 되돌린다.
        parseBehavior.set(SameStudentConcurrentRequestTest::parsed);
        transcriptService.uploadTranscript(memberId, pdf());
        return memberId;
    }

    // 검수 화면이 보내는 것처럼 기존 과목 전부 + 직접 추가한 과목 1개.
    private StudentCourseUpdateRequest currentCoursesPlusOneManual(long memberId) {
        List<CourseUpdateItem> items = new ArrayList<>(jdbc.query(
                "select id, raw_course_name from student_course where student_profile_id = ?",
                (rs, i) -> new CourseUpdateItem(rs.getLong(1), null, rs.getString(2), null, 3, null, 2024, null),
                profileId(memberId)));
        items.add(new CourseUpdateItem(null, null, "직접추가과목", null, 3, null, 2024, null));
        return new StudentCourseUpdateRequest(items);
    }

    // 전공 과목 10개가 있는 졸업사정표 파싱 결과.
    private static ParsedTranscript parsed() {
        List<Map<String, String>> courses = new ArrayList<>();
        for (int i = 0; i < COURSE_COUNT; i++) {
            courses.add(Map.of("section", "전공", "rawClassification", "05", "semester", "2024/1",
                    "courseCode", "CONC" + i, "courseName", "테스트과목" + i, "credits", "3"));
        }
        return new ParsedTranscript(
                Map.of("studentId", "2023000000", "academicStatus", "재학", "grade", "3", "department", "테스트학과"),
                Map.of("earnedCredits", "30", "requiredCredits", "130"),
                List.of(),
                List.of(Map.of("majorSequence", "1", "majorName", "테스트학과")),
                courses);
    }

    private static MockMultipartFile pdf() {
        return new MockMultipartFile("file", "transcript.pdf", "application/pdf", "%PDF-1.7".getBytes());
    }

    private long profileId(long memberId) {
        return jdbc.queryForObject("select id from student_profile where member_id = ?", Long.class, memberId);
    }

    private int courseCount(long memberId) {
        return jdbc.queryForObject("select count(*) from student_course where student_profile_id = ?",
                Integer.class, profileId(memberId));
    }

    private int manualCourseCount(long memberId) {
        return jdbc.queryForObject("select count(*) from student_course where student_profile_id = ? and source = 'MANUAL'",
                Integer.class, profileId(memberId));
    }

    private int mainMajorCount(long memberId) {
        return jdbc.queryForObject("select count(*) from student_major where student_profile_id = ? and major_type = 'MAIN'",
                Integer.class, profileId(memberId));
    }

    private Object onboardingConfirmedAt(long memberId) {
        return jdbc.queryForObject("select onboarding_confirmed_at from student_profile where member_id = ?",
                Object.class, memberId);
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("대기 시간 초과");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
