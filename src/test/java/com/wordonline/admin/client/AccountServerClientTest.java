package com.wordonline.admin.client;

import com.wordonline.admin.entity.server.Server;
import com.wordonline.admin.entity.server.ServerState;
import com.wordonline.admin.entity.server.ServerType;
import com.wordonline.admin.repository.server.ServerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers which address {@link AccountServerClient#init()} picks for the {@link RestClient} it
 * builds: the account server's {@code internalBaseUrl} when it reported one, its public
 * {@code getUrl()} otherwise. {@code login} is used only to trigger the lazy {@code init()}; the
 * request itself is expected to fail since the {@link RestClient} chain past {@code baseUrl} is
 * not stubbed.
 */
@ExtendWith(MockitoExtension.class)
class AccountServerClientTest {

    @Mock
    private RestClient.Builder builder;
    @Mock
    private ServerRepository serverRepository;
    @Mock
    private RestClient restClient;

    @Test
    void initUsesTheInternalBaseUrlWhenTheAccountServerReportedOne() {
        Server accountServer = new Server(1L, "https", "account.ac.yunseong.dev", 443,
                "http://account-server:8080", ServerType.ACCOUNT, ServerState.ACTIVE, null, null, null);
        when(serverRepository.findAllByTypeAndState(ServerType.ACCOUNT, ServerState.ACTIVE))
                .thenReturn(List.of(accountServer));
        when(builder.baseUrl(anyString())).thenReturn(builder);
        when(builder.build()).thenReturn(restClient);

        AccountServerClient client = new AccountServerClient(builder, serverRepository);
        assertThrows(RuntimeException.class, () -> client.login("user", "password"));

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(builder).baseUrl(urlCaptor.capture());
        assertEquals("http://account-server:8080", urlCaptor.getValue());
    }

    @Test
    void initFallsBackToThePublicUrlWhenTheAccountServerHasNoInternalBaseUrl() {
        Server accountServer = new Server(1L, "https", "account.ac.yunseong.dev", 443,
                null, ServerType.ACCOUNT, ServerState.ACTIVE, null, null, null);
        when(serverRepository.findAllByTypeAndState(ServerType.ACCOUNT, ServerState.ACTIVE))
                .thenReturn(List.of(accountServer));
        when(builder.baseUrl(anyString())).thenReturn(builder);
        when(builder.build()).thenReturn(restClient);

        AccountServerClient client = new AccountServerClient(builder, serverRepository);
        assertThrows(RuntimeException.class, () -> client.login("user", "password"));

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(builder).baseUrl(urlCaptor.capture());
        assertEquals("https://account.ac.yunseong.dev:443", urlCaptor.getValue());
    }
}
