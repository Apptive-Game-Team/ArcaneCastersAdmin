package com.wordonline.admin.security;

import com.wordonline.admin.entity.server.Server;
import com.wordonline.admin.entity.server.ServerState;
import com.wordonline.admin.entity.server.ServerType;
import com.wordonline.admin.repository.server.ServerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.JwtException;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Covers {@link AccountJwksDecoder#resolveJwkSetUri} and the failure path of
 * {@link AccountJwksDecoder#decode} at the configuration level, without touching the network:
 * building the real {@code NimbusJwtDecoder} only fetches keys lazily on first use, and the
 * missing-account-row case throws before that decoder is ever built, so neither path here reaches
 * out to a real JWKS endpoint.
 */
@ExtendWith(MockitoExtension.class)
class AccountJwksDecoderTest {

    @Mock
    private ServerRepository serverRepository;

    @Test
    void resolvesTheJwkSetUriFromTheInternalBaseUrlWhenTheAccountServerReportedOne() {
        Server accountServer = new Server(1L, "https", "account.ac.yunseong.dev", 443,
                "http://account-server:8080", ServerType.ACCOUNT, ServerState.ACTIVE, null, null, null);
        when(serverRepository.findAllByTypeAndState(ServerType.ACCOUNT, ServerState.ACTIVE))
                .thenReturn(List.of(accountServer));

        String jwkSetUri = AccountJwksDecoder.resolveJwkSetUri(serverRepository);

        assertEquals("http://account-server:8080/.well-known/jwks", jwkSetUri);
    }

    @Test
    void resolvesTheJwkSetUriFromThePublicUrlWhenTheAccountServerHasNoInternalBaseUrl() {
        Server accountServer = new Server(1L, "https", "account.ac.yunseong.dev", 443,
                null, ServerType.ACCOUNT, ServerState.ACTIVE, null, null, null);
        when(serverRepository.findAllByTypeAndState(ServerType.ACCOUNT, ServerState.ACTIVE))
                .thenReturn(List.of(accountServer));

        String jwkSetUri = AccountJwksDecoder.resolveJwkSetUri(serverRepository);

        assertEquals("https://account.ac.yunseong.dev:443/.well-known/jwks", jwkSetUri);
    }

    @Test
    void doesNotDoubleTheSlashWhenTheInternalBaseUrlAlreadyEndsInOne() {
        Server accountServer = new Server(1L, "https", "account.ac.yunseong.dev", 443,
                "http://account-server:8080/", ServerType.ACCOUNT, ServerState.ACTIVE, null, null, null);
        when(serverRepository.findAllByTypeAndState(ServerType.ACCOUNT, ServerState.ACTIVE))
                .thenReturn(List.of(accountServer));

        String jwkSetUri = AccountJwksDecoder.resolveJwkSetUri(serverRepository);

        assertEquals("http://account-server:8080/.well-known/jwks", jwkSetUri);
    }

    @Test
    void resolvingTheUriThrowsWhenNoActiveAccountRowExists() {
        when(serverRepository.findAllByTypeAndState(ServerType.ACCOUNT, ServerState.ACTIVE))
                .thenReturn(Collections.emptyList());

        assertThrows(JwtException.class, () -> AccountJwksDecoder.resolveJwkSetUri(serverRepository));
    }

    @Test
    void decodeFailsInsteadOfPassingWithoutVerificationWhenNoActiveAccountRowExists() {
        when(serverRepository.findAllByTypeAndState(ServerType.ACCOUNT, ServerState.ACTIVE))
                .thenReturn(Collections.emptyList());

        AccountJwksDecoder decoder = new AccountJwksDecoder(serverRepository);

        // No account row means the URI can never be resolved, so decode must reject the token
        // rather than skip verification. JwtAuthenticationFilter treats any exception here as
        // "leave the request unauthenticated," so this failure is what keeps protected endpoints
        // rejected instead of silently open.
        assertThrows(JwtException.class, () -> decoder.decode("any-token"));
    }
}
