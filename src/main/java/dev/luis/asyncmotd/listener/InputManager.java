package dev.luis.asyncmotd.listener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InputManager {

    public record Session(String preset, String field, long expiresAt) {
    }

    private final Map<UUID, Session> pending = new ConcurrentHashMap<>();

    public void request(UUID player, String preset, String field, long timeoutSeconds) {
        pending.put(player, new Session(preset, field, System.currentTimeMillis() + timeoutSeconds * 1000L));
    }

    public Session consume(UUID player) {
        return pending.remove(player);
    }

    public void clear(UUID player) {
        pending.remove(player);
    }
}
