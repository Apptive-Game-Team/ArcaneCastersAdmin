package com.wordonline.admin.client;

import com.wordonline.admin.dto.auth.LoginRequestDto;
import com.wordonline.admin.dto.auth.TokenResponseDto;
import com.wordonline.admin.entity.server.Server;
import com.wordonline.admin.entity.server.ServerState;
import com.wordonline.admin.entity.server.ServerType;
import com.wordonline.admin.repository.server.ServerRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.function.Function;

@Slf4j
@Component
public class AccountServerClient {
    private final RestClient.Builder builder;
    private final ServerRepository serverRepository;

    private volatile RestClient restClient;
    private volatile String activeBaseUrl;
    private volatile String publicBaseUrl;

    public AccountServerClient(@Qualifier("accountAuthRestClientBuilder") RestClient.Builder builder,
                               ServerRepository serverRepository) {
        this.builder = builder;
        this.serverRepository = serverRepository;
    }

    private synchronized void init() {
        if (restClient != null) {
            return;
        }
        Server accountServer = serverRepository.findAllByTypeAndState(ServerType.ACCOUNT, ServerState.ACTIVE)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Account server not found in database. Please configure an ACTIVE ACCOUNT server."
                ));
        publicBaseUrl = accountServer.getUrl();
        activeBaseUrl = accountServer.getInterServerUrl();
        restClient = authClient(activeBaseUrl);
        log.info("Account server client initialized with URL: {}", activeBaseUrl);
    }

    /**
     * Switches the cached {@link RestClient} to the account server's public address and keeps it
     * there for every later call. Idempotent: a second caller that loses the race to the first
     * fallback finds {@code activeBaseUrl} already equal to {@code publicBaseUrl} and does nothing.
     */
    private synchronized void switchToPublicBaseUrl() {
        if (activeBaseUrl.equals(publicBaseUrl)) {
            return;
        }
        restClient = authClient(publicBaseUrl);
        activeBaseUrl = publicBaseUrl;
        log.info("Account server client switched to public URL: {}", publicBaseUrl);
    }

    /**
     * Single entry point every account server call goes through. Runs {@code call} against the
     * cached {@link RestClient}. If the active address is still the internal one and the call
     * cannot connect at all — timeout, connection refused, unresolvable host, surfaced by
     * {@link RestClient} as a {@link ResourceAccessException} — falls back to the public address
     * once and retries. An HTTP 4xx/5xx response means the address was reached and the server
     * answered, so {@link org.springframework.web.client.RestClientResponseException} and its
     * subclasses are never retried here.
     */
    private <T> T callAccountServer(Function<RestClient, T> call) {
        if (restClient == null) {
            init();
        }
        try {
            return call.apply(restClient);
        } catch (ResourceAccessException e) {
            if (activeBaseUrl.equals(publicBaseUrl)) {
                throw e;
            }
            log.warn("Account server internal address {} unreachable, falling back to public address {}",
                    activeBaseUrl, publicBaseUrl, e);
            switchToPublicBaseUrl();
            return call.apply(restClient);
        }
    }

    private RestClient authClient(String baseUrl) {
        return builder.clone().baseUrl(baseUrl).build();
    }

    public TokenResponseDto login(String username, String password) {
        LoginRequestDto loginRequest = new LoginRequestDto(username, password);

        try {
            TokenResponseDto response = callAccountServer(client -> client.post()
                    .uri("/api/members/login")
                    .body(loginRequest)
                    .retrieve()
                    .body(TokenResponseDto.class));

            if (response == null || response.getJwt() == null) {
                log.error("Login failed for user: {} - empty response", username);
                throw new IllegalStateException("Account server returned an empty login response");
            }

            log.info("Login successful for user: {}", username);
            return response;
        } catch (Exception e) {
            log.error("Login failed for user: {}", username, e);
            if (e instanceof IllegalStateException) {
                throw e;
            }
            throw new RuntimeException("Login request to account server failed", e);
        }
    }

    public TokenResponseDto refresh(String refreshToken) {
        if (restClient == null) {
            init();
        }
        try {
            return restClient.post().uri("/api/auth/refresh")
                    .body(Map.of("refreshToken", refreshToken))
                    .retrieve().body(TokenResponseDto.class);
        } catch (ResourceAccessException e) {
            // 요청이 이미 처리됐을 수 있으므로 같은 토큰으로 자동 재시도하지 않는다.
            switchToPublicBaseUrl();
            throw e;
        }
    }

    public void logout(String refreshToken) {
        callAccountServer(client -> client.post().uri("/api/auth/logout")
                .body(Map.of("refreshToken", refreshToken))
                .retrieve().toBodilessEntity());
    }
}
