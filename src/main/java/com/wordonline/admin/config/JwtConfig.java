package com.wordonline.admin.config;

import com.wordonline.admin.repository.server.ServerRepository;
import com.wordonline.admin.security.AccountJwksDecoder;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@Configuration
@RequiredArgsConstructor
public class JwtConfig {

    private final ServerRepository serverRepository;

    /**
     * The ACCOUNT server's address lives in the {@code servers} table, which is not queryable
     * while this bean is being constructed. {@link AccountJwksDecoder} defers resolving the JWKS
     * URI to the first {@code decode} call, mirroring {@code AccountServerClient.init()}.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        return new AccountJwksDecoder(serverRepository);
    }
}
