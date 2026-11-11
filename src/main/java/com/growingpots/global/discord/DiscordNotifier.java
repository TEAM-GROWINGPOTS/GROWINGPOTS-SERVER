package com.growingpots.global.discord;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class DiscordNotifier {

    @Value("${discord.webhook-url:}")
    private String webhookUrl;

    // 타임아웃이 없으면 Discord가 응답하지 않을 때 알림을 보내는 요청 스레드가 무한히 기다린다.
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private final RestClient restClient = RestClient.builder()
            .requestFactory(createRequestFactory())
            .build();

    private static JdkClientHttpRequestFactory createRequestFactory() {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return requestFactory;
    }

    public void sendError(String title, String message) {
        if (webhookUrl.isBlank()) return;

        Map<String, Object> body = Map.of(
                "embeds", List.of(Map.of(
                        "title", title,
                        "description", message,
                        "color", 15548997
                ))
        );

        try {
            restClient.post()
                    .uri(webhookUrl)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("[DiscordNotifier] 알림 전송 실패: {}", e.getMessage());
        }
    }
}
