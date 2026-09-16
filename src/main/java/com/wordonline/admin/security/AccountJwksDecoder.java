package com.wordonline.admin.security;

import com.wordonline.admin.entity.server.Server;
import com.wordonline.admin.entity.server.ServerState;
import com.wordonline.admin.entity.server.ServerType;
import com.wordonline.admin.repository.server.ServerRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * {@link JwtDecoder} that verifies tokens against the ACCOUNT server's JWKS endpoint
 * ({@code /.well-known/jwks}). The account server's address lives in the {@code servers} table
 * (read through {@link ServerRepository}), which is not queryable while Spring is still
 * constructing beans, so the underlying {@link NimbusJwtDecoder} is built lazily on the first
 * {@link #decode} call instead of at bean-construction time — the same lazy-address pattern
 * {@code AccountServerClient.init()} uses for its {@code RestClient}. Once resolved, the address
 * is cached for the life of this decoder; a later address change is not re-resolved here, again
 * matching {@code AccountServerClient}'s own caching.
 *
 * <p>When no ACTIVE ACCOUNT row exists, every {@link #decode} call throws a {@link JwtException}
 * instead of building a decoder or accepting the token, so {@code JwtAuthenticationFilter} leaves
 * the request unauthenticated and protected endpoints stay rejected rather than passing without
 * verification.
 */
@Slf4j
public class AccountJwksDecoder implements JwtDecoder {

    private static final String JWKS_PATH = "/.well-known/jwks";

    private final ServerRepository serverRepository;
    private volatile JwtDecoder delegate;

    public AccountJwksDecoder(ServerRepository serverRepository) {
        this.serverRepository = serverRepository;
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        return delegate().decode(token);
    }

    private JwtDecoder delegate() {
        JwtDecoder current = delegate;
        if (current == null) {
            synchronized (this) {
                current = delegate;
                if (current == null) {
                    String jwkSetUri = resolveJwkSetUri(serverRepository);
                    current = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
                    log.info("JWT decoder initialized against JWKS URI: {}", jwkSetUri);
                    delegate = current;
                }
            }
        }
        return current;
    }

    /**
     * Builds the JWKS URI from the ACCOUNT server's {@link Server#getInterServerUrl()} (its
     * internal address when it reported one, otherwise its public address) plus
     * {@value #JWKS_PATH}, without producing a double slash when the base URL already ends in
     * one. Throws {@link JwtException} when no ACTIVE ACCOUNT row exists, so a missing account
     * configuration fails token verification instead of skipping it.
     *
     * <p>Package-private and static so this string assembly can be tested directly against a
     * mocked {@link ServerRepository} without touching the network — building the real
     * {@link NimbusJwtDecoder} would only fetch keys on first use, but there is no need to invoke
     * it at all to check which URI it was given.
     */
    static String resolveJwkSetUri(ServerRepository serverRepository) {
        Server accountServer = serverRepository.findAllByTypeAndState(ServerType.ACCOUNT, ServerState.ACTIVE)
                .stream()
                .findFirst()
                .orElseThrow(() -> new JwtException(
                        "Account server not found in database. Please configure an ACTIVE ACCOUNT server."));

        String baseUrl = accountServer.getInterServerUrl();
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return normalizedBaseUrl + JWKS_PATH;
    }
}
