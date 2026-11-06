package com.growingpots.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Properties;
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

// dev 설정 파일을 그대로 불러와 actuator는 내부 관리 포트로만 열리고, 프론트 협업용 Swagger는 유지되는지 확인한다(#295).
// 테스트는 기본으로 지표 내보내기를 꺼서 /actuator/prometheus가 아예 없다. 실제와 같게 켠다.
@AutoConfigureObservability(tracing = false)
@ActiveProfiles({"dev", "test"})
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"management.server.port=0", "discord.webhook-url="})
class DevActuatorExposureTest {

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
        YamlPropertiesFactoryBean dev = new YamlPropertiesFactoryBean();
        dev.setResources(new ClassPathResource("application-dev.yml"));
        Properties properties = dev.getObject();

        String managementPort = properties.getProperty("management.server.port");

        assertThat(managementPort).isNotBlank();
        assertThat(managementPort).isNotEqualTo(properties.getProperty("server.port"));
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

    @Test
    void 앱_포트에서_Swagger와_API_문서를_조회할_수_있다() throws Exception {
        assertThat(get(serverPort, "/v3/api-docs").statusCode()).isEqualTo(200);
        assertThat(get(serverPort, "/swagger-ui/index.html").statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> get(int port, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
