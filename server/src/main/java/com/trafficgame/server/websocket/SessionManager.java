package com.trafficgame.server.websocket;

import com.trafficgame.game.TrafficGame;
import com.trafficgame.game.commands.CommandHandler;
import com.trafficgame.shared.protocol.PlayerCommand;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.*;

/**
 * Manages game sessions — one per connected player.
 * Each session has its own TrafficGame instance ticked at 10 Hz.
 */
@Singleton
public final class SessionManager {

    private static final Logger LOG = LoggerFactory.getLogger(SessionManager.class);

    private final Map<String, GameSession> sessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(
            Runtime.getRuntime().availableProcessors());

    public GameSession createSession(String sessionId) {
        TrafficGame game = new TrafficGame(sessionId.hashCode());
        CommandHandler commandHandler = new CommandHandler(game);
        GameSession session = new GameSession(sessionId, game, commandHandler);
        sessions.put(sessionId, session);
        LOG.info("Session created: {}", sessionId);
        return session;
    }

    public GameSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public void startSession(String sessionId) {
        GameSession session = sessions.get(sessionId);
        if (session == null) return;

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                session.getGame()::tick,
                0, 100, TimeUnit.MILLISECONDS); // 10 Hz
        session.setTickFuture(future);
        LOG.info("Session started: {}", sessionId);
    }

    public void stopSession(String sessionId) {
        GameSession session = sessions.remove(sessionId);
        if (session != null) {
            session.stop();
            LOG.info("Session stopped: {}", sessionId);
        }
    }

    public void handleCommand(String sessionId, PlayerCommand command) {
        GameSession session = sessions.get(sessionId);
        if (session == null) return;
        session.handleCommand(command);
    }

    public int getActiveSessionCount() {
        return sessions.size();
    }

    @PreDestroy
    public void shutdown() {
        LOG.info("Shutting down {} game sessions", sessions.size());
        sessions.values().forEach(GameSession::stop);
        sessions.clear();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /* ── inner session holder ───────────────────── */

    public static final class GameSession {
        private final String id;
        private final TrafficGame game;
        private final CommandHandler commandHandler;
        private ScheduledFuture<?> tickFuture;

        GameSession(String id, TrafficGame game, CommandHandler commandHandler) {
            this.id = id;
            this.game = game;
            this.commandHandler = commandHandler;
        }

        public String getId() { return id; }
        public TrafficGame getGame() { return game; }

        void setTickFuture(ScheduledFuture<?> future) { this.tickFuture = future; }

        void handleCommand(PlayerCommand command) {
            if (command == null || command.getType() == null) return;
            switch (command.getType()) {
                case "place_road" -> commandHandler.handlePlaceRoad(
                        command.getX(), command.getY(), command.getX2(), command.getY2(),
                        command.getData() != null ? command.getData() : "LOCAL");
                case "place_signal" -> commandHandler.handlePlaceSignal(command.getTargetId());
                case "demolish" -> commandHandler.handleDemolish(command.getTargetId());
            }
        }

        void stop() {
            if (tickFuture != null) tickFuture.cancel(false);
        }
    }
}
