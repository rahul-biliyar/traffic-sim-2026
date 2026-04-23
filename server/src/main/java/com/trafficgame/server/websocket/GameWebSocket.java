package com.trafficgame.server.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trafficgame.shared.protocol.PlayerCommand;
import com.trafficgame.shared.protocol.TickDelta;
import io.micronaut.websocket.WebSocketBroadcaster;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.*;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.*;

/**
 * WebSocket endpoint for real-time game communication.
 * Each connection creates/joins a game session.
 */
@ServerWebSocket("/ws/game/{sessionId}")
public class GameWebSocket {

    private static final Logger LOG = LoggerFactory.getLogger(GameWebSocket.class);

    private final SessionManager sessionManager;
    private final ObjectMapper objectMapper;
    private final Map<String, ScheduledFuture<?>> broadcastFutures = new ConcurrentHashMap<>();
    private final ScheduledExecutorService broadcastScheduler = Executors.newScheduledThreadPool(2);

    @Inject
    public GameWebSocket(SessionManager sessionManager, ObjectMapper objectMapper) {
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
    }

    @OnOpen
    public void onOpen(String sessionId, WebSocketSession wsSession) {
        LOG.info("Client connected: sessionId={}", sessionId);

        SessionManager.GameSession session = sessionManager.getSession(sessionId);
        if (session == null) {
            session = sessionManager.createSession(sessionId);
            sessionManager.startSession(sessionId);
        }

        // Send initial snapshot
        try {
            var snapshot = MessageBuilder.buildSnapshot(session.getGame());
            String json = objectMapper.writeValueAsString(Map.of("type", "snapshot", "data", snapshot));
            wsSession.sendSync(json);
        } catch (Exception e) {
            LOG.error("Failed to send snapshot", e);
        }

        // Start broadcasting tick deltas
        final String sid = sessionId;
        ScheduledFuture<?> future = broadcastScheduler.scheduleAtFixedRate(() -> {
            try {
                SessionManager.GameSession gs = sessionManager.getSession(sid);
                if (gs == null) return;
                TickDelta delta = MessageBuilder.buildTickDelta(gs.getGame());
                String json = objectMapper.writeValueAsString(Map.of("type", "tick", "data", delta));
                if (wsSession.isOpen()) {
                    wsSession.sendSync(json);
                }
            } catch (Exception e) {
                LOG.debug("Broadcast error: {}", e.getMessage());
            }
        }, 100, 100, TimeUnit.MILLISECONDS);
        broadcastFutures.put(sessionId + ":" + wsSession.getId(), future);
    }

    @OnMessage
    public void onMessage(String sessionId, String message, WebSocketSession wsSession) {
        try {
            PlayerCommand command = objectMapper.readValue(message, PlayerCommand.class);
            sessionManager.handleCommand(sessionId, command);

            // After any road/signal/district mutation, push a fresh snapshot so
            // the client re-renders the updated geometry immediately.
            if (isMutationCommand(command.getType())) {
                SessionManager.GameSession session = sessionManager.getSession(sessionId);
                if (session != null && wsSession.isOpen()) {
                    var snapshot = MessageBuilder.buildSnapshot(session.getGame());
                    String json = objectMapper.writeValueAsString(
                            Map.of("type", "snapshot", "data", snapshot));
                    wsSession.sendSync(json);
                }
            }
        } catch (Exception e) {
            LOG.warn("Invalid command from session {}: {}", sessionId, e.getMessage());
        }
    }

    private boolean isMutationCommand(String type) {
        return type != null && switch (type) {
            case "upgrade_road", "place_road", "place_signal",
                 "demolish", "unlock_district" -> true;
            default -> false;
        };
    }

    @OnClose
    public void onClose(String sessionId, WebSocketSession wsSession) {
        LOG.info("Client disconnected: sessionId={}", sessionId);
        String key = sessionId + ":" + wsSession.getId();
        ScheduledFuture<?> future = broadcastFutures.remove(key);
        if (future != null) future.cancel(false);
    }

    @OnError
    public void onError(String sessionId, WebSocketSession wsSession, Throwable error) {
        LOG.error("WebSocket error for session {}: {}", sessionId, error.getMessage());
        onClose(sessionId, wsSession);
    }
}
