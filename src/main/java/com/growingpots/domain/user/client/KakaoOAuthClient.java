package com.growingpots.domain.user.client;

import com.growingpots.global.exception.BaseException;
import com.growingpots.global.response.error.ErrorCode;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// 프론트에서 카카오 SDK로 access token을 발급받아 넘겨주는 방식
// 인가 코드 교환 없이 이 토큰으로 사용자 정보만 조회/검증한다.

@Component
public class KakaoOAuthClient {

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

    @Value("${oauth.kakao.user-info-uri}")
    private String userInfoUri;

    public KakaoUserInfoResponse getUserInfo(String oauthAccessToken) {
        try {
            return restClient.get()
                    .uri(userInfoUri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + oauthAccessToken)
                    .retrieve()
                    .body(KakaoUserInfoResponse.class);
        }
        // 사용자가 이메일 제공에 동의 안 하면 카카오가 kakao_account 자체를 안내려줄 수 있음
        catch (HttpClientErrorException.Unauthorized e) {
            throw new BaseException(ErrorCode.INVALID_TOKEN, e.getMessage());
        } catch (RestClientException e) {
            throw new BaseException(ErrorCode.OAUTH_SERVER_ERROR, e.getMessage());
        }
    }
}
