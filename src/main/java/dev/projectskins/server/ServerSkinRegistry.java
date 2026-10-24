package dev.projectskins.server;

import dev.projectskins.network.SkinSyncPacket;
import dev.projectskins.skin.SkinSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Авторитетное состояние назначений одной серверной сессии. Все методы вызываются
 * только из основного серверного потока — из команд и событий входа/остановки.
 * Запись привязана к UUID, а не к экземпляру сущности, поэтому переживает смерть,
 * смену измерения и повторный вход. После остановки сервера назначения очищаются;
 * сохранение скинов между перезапусками в этой версии не предусмотрено.
 */
public final class ServerSkinRegistry {
    private final Map<UUID, SkinSyncPacket> assignments = new LinkedHashMap<>();
    private long revision;

    /** Новое назначение всегда получает больший номер, даже при повторном выборе URL. */
    public SkinSyncPacket assign(UUID playerId, SkinSource source) {
        long nextRevision = Math.incrementExact(revision);
        SkinSyncPacket packet = new SkinSyncPacket(playerId, source, nextRevision);
        assignments.put(playerId, packet);
        revision = nextRevision;
        return packet;
    }

    /** Неизменяемая копия не позволяет вызывающему коду повредить реестр. */
    public List<SkinSyncPacket> snapshot() {
        return List.copyOf(assignments.values());
    }

    public void clear() {
        assignments.clear();
        revision = 0;
    }
}
