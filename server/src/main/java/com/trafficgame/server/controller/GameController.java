package com.trafficgame.server.controller;

import com.trafficgame.server.websocket.MessageBuilder;
import com.trafficgame.server.websocket.SessionManager;
import com.trafficgame.shared.protocol.GameStateSnapshot;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import jakarta.inject.Inject;

import java.util.Map;

/**
 * REST controller for non-realtime game operations.
 */
@Controller("/api/game")
public class GameController {

    private final SessionManager sessionManager;

    @Inject
    public GameController(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Post("/new")
    public HttpResponse<Map<String, Object>> newGame() {
        String sessionId = "game_" + System.currentTimeMillis();
        sessionManager.createSession(sessionId);
        sessionManager.startSession(sessionId);
        return HttpResponse.ok(Map.of("sessionId", sessionId, "status", "created"));
    }

    @Get("/status/{sessionId}")
    public HttpResponse<?> getStatus(@PathVariable String sessionId) {
        SessionManager.GameSession session = sessionManager.getSession(sessionId);
        if (session == null) {
            return HttpResponse.notFound(Map.of("error", "Session not found"));
        }
        GameStateSnapshot snapshot = MessageBuilder.buildSnapshot(session.getGame());
        return HttpResponse.ok(snapshot);
    }

    @Get("/health")
    public HttpResponse<Map<String, Object>> health() {
        return HttpResponse.ok(Map.of(
                "status", "UP",
                "activeSessions", sessionManager.getActiveSessionCount()));
    }
}
