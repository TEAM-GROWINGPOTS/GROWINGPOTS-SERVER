package com.growingpots.global.discord;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DiscordNotifierTest {

    // 연결은 받지만 응답을 주지 않는 서버. 타임아웃이 없으면 sendError가 끝나지 않는다.
    @Test
    void Discord가_응답하지_않아도_타임아웃_후_끝난다() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            Thread acceptor = new Thread(() -> {
                try (Socket ignored = server.accept()) {
                    Thread.sleep(30_000);
                } catch (Exception ignored) {
                }
            });
            acceptor.setDaemon(true);
            acceptor.start();

            DiscordNotifier notifier = new DiscordNotifier();
            ReflectionTestUtils.setField(notifier, "webhookUrl", "http://127.0.0.1:" + server.getLocalPort());

            long begin = System.nanoTime();
            notifier.sendError("title", "message");

            assertThat(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - begin)).isLessThan(10);
        }
    }
}
