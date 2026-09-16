package com.wordonline.admin.entity.server;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "servers")
public class Server {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String protocol;
    private String domain;
    private Integer port;

    /**
     * Base URL for server-to-server calls on the private network, e.g. {@code http://account-server:8080}
     * (no trailing slash). Written by the owning server itself. {@code null} means it was never
     * reported; callers then fall back to {@link #getUrl()}.
     */
    private String internalBaseUrl;

    @Enumerated(EnumType.STRING)
    private ServerType type;

    @Setter
    @Enumerated(EnumType.STRING)
    private ServerState state;

    /** Written by the game server on every heartbeat. Read-only here. */
    private Integer sessionCount;

    /** Written by the game server on every heartbeat. {@code null} means it never reported. */
    private Instant lastHeartbeatAt;

    /**
     * Override for the game server's bot scheduler target session count. Written here, read by
     * the game server on every scheduler tick. {@code null} means no override; the game server
     * then uses its configured default. Updated only through the targeted repository query so a
     * whole-entity save cannot race the game server's heartbeat writes.
     */
    private Integer targetBotSessions;

    public String getUrl() {
        return String.format("%s://%s:%d", protocol, domain, port);
    }

    /**
     * Base URL to use for server-to-server calls: {@link #internalBaseUrl} when this server
     * reported one, otherwise the public {@link #getUrl()}. Chosen once here from whichever
     * value is present; a caller whose internal address turns out to be unreachable is
     * responsible for falling back to {@link #getUrl()} itself (see {@code AccountServerClient}).
     */
    public String getInterServerUrl() {
        return internalBaseUrl != null ? internalBaseUrl : getUrl();
    }

    public Server(Long id, String protocol, String domain, Integer port, ServerType type, ServerState state,
                  Integer sessionCount, Instant lastHeartbeatAt) {
        this(id, protocol, domain, port, null, type, state, sessionCount, lastHeartbeatAt, null);
    }

    public Server(Long id, String protocol, String domain, Integer port, ServerType type, ServerState state) {
        this(id, protocol, domain, port, null, type, state, null, null, null);
    }

    public Server(String protocol, String domain, int port, ServerType serverType, ServerState state) {
        this(null, protocol, domain, port, serverType, state);
    }
}
