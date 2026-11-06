package com.growingpots.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementServerProperties;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

// prod 설정 파일을 그대로 불러와 actuator가 외부에 노출되지 않는지 확인한다(#295).
// 포트만 테스트용 임의 포트로 바꾸고, 나머지 값은 application-prod.yml을 따른다.
// 테스트는 기본으로 지표 내보내기를 꺼서 /actuator/prometheus가 아예 없다. 실제와 같게 켠다.
@AutoConfigureObservability(tracing = false)
@ActiveProfiles({"prod", "test"})
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"management.server.port=0", "discord.webhook-url="})
class ProdActuatorExposureTest {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int serverPort;

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private ManagementServerProperties managementServerProperties;

    // 위 테스트들은 관리 포트를 임의 포트로 덮어쓰므로, 설정 파일에서 포트가 빠져도 통과한다. 파일을 직접 확인한다.
    @Test
    void 설정_파일에서_관리_포트가_앱_포트와_분리돼_있다() {
        YamlPropertiesFactoryBean base = new YamlPropertiesFactoryBean();
        base.setResources(new ClassPathResource("application.yml"));
        YamlPropertiesFactoryBean prod = new YamlPropertiesFactoryBean();
        prod.setResources(new ClassPathResource("application-prod.yml"));

        String managementPort = prod.getObject().getProperty("management.server.port");

        assertThat(managementPort).isNotBlank();
        assertThat(managementPort).isNotEqualTo(base.getObject().getProperty("server.port"));
    }

    @Test
    void 관리_포트는_서버_내부에서만_접근할_수_있다() throws Exception {
        assertThat(managementServerProperties.getAddress()).isEqualTo(InetAddress.getByName("127.0.0.1"));
    }

    @Test
    void 관리_포트에서_인증_없이_지표와_헬스체크를_조회할_수_있다() throws Exception {
        assertThat(get(managementPort, "/actuator/prometheus").statusCode()).isEqualTo(200);
        assertThat(get(managementPort, "/actuator/health").statusCode()).isEqualTo(200);
    }

    @Test
    void 앱_포트에서는_지표를_조회할_수_없다() throws Exception {
        HttpResponse<String> response = get(serverPort, "/actuator/prometheus");

        assertThat(response.statusCode()).isNotEqualTo(200);
        assertThat(response.body()).doesNotContain("jvm_memory");
    }

    private HttpResponse<String> get(int port, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
