package com.growingpots.global.discord;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class DiscordNotifier {

    @Value("${discord.webhook-url:}")
    private String webhookUrl;

    private final RestClient restClient = RestClient.create();

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
