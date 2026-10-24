package dev.projectskins.client;

import dev.projectskins.skin.SkinSource;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Хранит последние назначения скинов независимо от Minecraft и загрузчика текстур.
 * Серверная ревизия защищает от повторных и запоздавших пакетов, а номер сессии —
 * от результатов загрузки, завершившейся уже после подключения к другому серверу.
 * Все операции синхронизированы; наружу выдаются только неизменяемые запросы
 * и снимки коллекции, поэтому вызывающий код не может изменить внутреннее состояние.
 */
public final class SkinAssignments {
    private final Map<UUID, Request> latest = new HashMap<>();
    private long session;

    /**
     * Неизменяемый маркер конкретного назначения. Его нужно сохранить до конца
     * асинхронной загрузки и проверить через {@link SkinAssignments#isCurrent(Request)} перед
     * применением результата. Одинаковый источник в новом запросе не делает
     * старую загрузку актуальной.
     */
    public record Request(long session, UUID playerId, SkinSource source, long revision) {
        public Request {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(source, "source");
            if (revision <= 0) {
                throw new IllegalArgumentException("Skin revision must be positive");
            }
        }
    }

    /**
     * Принимает только более новую ревизию для данного игрока.
     * Возвращает null для повтора или старого пакета: повторно скачивать его не нужно.
     */
    public synchronized Request accept(UUID playerId, SkinSource source, long revision) {
        Request candidate = new Request(session, playerId, source, revision);
        Request previous = latest.get(playerId);
        if (previous != null && revision <= previous.revision()) {
            return null;
        }
        latest.put(playerId, candidate);
        return candidate;
    }

    /**
     * Разрешает применить только тот запрос, который всё ещё является последним.
     * Сравнение по идентичности дополнительно исключает подставной маркер,
     * созданный вне этого хранилища с такими же полями.
     */
    public synchronized boolean isCurrent(Request request) {
        return request != null
                && request.session() == session
                && latest.get(request.playerId()) == request;
    }

    /** Возвращает неизменяемый снимок назначений, например для перезагрузки ресурсов. */
    public synchronized Collection<Request> requests() {
        return List.copyOf(latest.values());
    }

    /**
     * Начинает новую клиентскую сессию. Старые загрузки теряют актуальность,
     * а новый сервер снова может прислать ревизию 1 для того же UUID.
     */
    public synchronized void reset() {
        session++;
        latest.clear();
    }
}
