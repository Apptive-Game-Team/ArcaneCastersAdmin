package com.wordonline.admin.client;

import com.wordonline.admin.entity.server.Server;
import com.wordonline.admin.entity.server.ServerState;
import com.wordonline.admin.entity.server.ServerType;
import com.wordonline.admin.repository.server.ServerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Covers the connection-failure fallback in {@link AccountServerClient#callAccountServer}: a call
 * to the account server's internal address that fails to connect (surfaced by {@link RestClient}
 * as a {@code ResourceAccessException}) retries once against the public address and, on success,
 * keeps every later call on the public address. An HTTP error response from the internal address
 * is a different case — the address was reached — and must not trigger the fallback.
 *
 * <p>Uses a real {@link RestClient.Builder} bound to {@link MockRestServiceServer} instead of a
 * mocked {@link RestClient}, because the fallback has to be driven by genuine exception types
 * ({@code ResourceAccessException} vs. {@code RestClientResponseException}) that only the real
 * request pipeline produces.
 */
@ExtendWith(MockitoExtension.class)
class AccountServerClientFallbackTest {

    private static final String INTERNAL_URL = "http://account-server:8080";
    private static final String PUBLIC_URL = "https://account.ac.yunseong.dev:443";
    private static final String LOGIN_PATH = "/api/members/login";

    @Mock
    private ServerRepository serverRepository;

    private MockRestServiceServer mockServer;
    private AccountServerClient client;

    @BeforeEach
    void setUp() {
        Server accountServer = new Server(1L, "https", "account.ac.yunseong.dev", 443, INTERNAL_URL,
                ServerType.ACCOUNT, ServerState.ACTIVE, null, null, null);
        when(serverRepository.findAllByTypeAndState(ServerType.ACCOUNT, ServerState.ACTIVE))
                .thenReturn(List.of(accountServer));

        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new AccountServerClient(builder, serverRepository);
    }

    @Test
    void doesNotRetryWhenTheInternalAddressSucceeds() {
        mockServer.expect(requestTo(INTERNAL_URL + LOGIN_PATH))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"jwt\":\"internal-token\"}", MediaType.APPLICATION_JSON));

        String jwt = client.login("user", "password");

        assertEquals("internal-token", jwt);
        mockServer.verify();
    }

    @Test
    void fallsBackToThePublicAddressOnceWhenTheInternalAddressCannotBeReachedThenStaysThere() {
        // All three expected requests are registered up front — MockRestServiceServer refuses to
        // add more once requests start arriving. The second login's expectation is `requestTo`
        // the public address only: if the client wrongly tried the internal address again first,
        // that request would not match any remaining expectation and the test would fail.
        mockServer.expect(requestTo(INTERNAL_URL + LOGIN_PATH))
                .andExpect(method(HttpMethod.POST))
                .andRespond(request -> {
                    throw new IOException("Connection refused");
                });
        mockServer.expect(requestTo(PUBLIC_URL + LOGIN_PATH))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"jwt\":\"public-token\"}", MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(PUBLIC_URL + LOGIN_PATH))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"jwt\":\"public-token-2\"}", MediaType.APPLICATION_JSON));

        String firstJwt = client.login("user", "password");
        assertEquals("public-token", firstJwt);

        // A second call must go straight to the public address: no further attempt at the
        // internal one, and no second fallback request either.
        String secondJwt = client.login("user", "password");
        assertEquals("public-token-2", secondJwt);

        mockServer.verify();
    }

    @Test
    void doesNotFallBackWhenTheInternalAddressAnswersWithAnHttpErrorStatus() {
        mockServer.expect(requestTo(INTERNAL_URL + LOGIN_PATH))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        assertThrows(RuntimeException.class, () -> client.login("user", "password"));

        // Only the one expectation above was registered; had the client fallen back to the
        // public address, the unmatched request would have failed this verification too.
        mockServer.verify();
    }
}
