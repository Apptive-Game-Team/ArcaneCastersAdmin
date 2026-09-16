package com.wordonline.admin.entity.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerTest {

    @Test
    void getInterServerUrlUsesTheInternalBaseUrlWhenTheServerReportedOne() {
        Server server = new Server(1L, "https", "account.ac.yunseong.dev", 443, "http://account-server:8080",
                ServerType.ACCOUNT, ServerState.ACTIVE, null, null, null);

        assertEquals("http://account-server:8080", server.getInterServerUrl());
    }

    @Test
    void getInterServerUrlFallsBackToThePublicUrlWhenTheInternalBaseUrlIsNull() {
        Server server = new Server(1L, "https", "account.ac.yunseong.dev", 443, null,
                ServerType.ACCOUNT, ServerState.ACTIVE, null, null, null);

        assertEquals("https://account.ac.yunseong.dev:443", server.getInterServerUrl());
    }
}
