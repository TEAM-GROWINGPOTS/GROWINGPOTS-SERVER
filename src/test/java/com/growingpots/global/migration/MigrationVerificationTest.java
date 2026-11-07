package com.growingpots.global.migration;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// 다른 테스트는 H2에 엔티티로 스키마를 만들어서 마이그레이션 파일이 실제로 도는지는 확인하지 못한다(#284).
// 실제 MySQL의 빈 DB에 마이그레이션을 전부 적용하고, 그 결과가 엔티티와 맞는지 Hibernate validate로 확인한다.
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
class MigrationVerificationTest {

    @Autowired
    private Flyway flyway;

    // 컨텍스트가 떴다는 것 자체가 마이그레이션 적용과 ddl-auto: validate를 통과했다는 뜻이다.
    // 엔티티만 바꾸고 마이그레이션을 빠뜨리면 컨텍스트 로딩에서 실패한다.
    @Test
    void 마이그레이션을_모두_적용하면_스키마가_엔티티와_일치한다() {
        MigrationInfo[] all = flyway.info().all();

        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.info().current().getVersion()).isEqualTo(all[all.length - 1].getVersion());
    }
}
