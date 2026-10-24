package dev.projectskins.network;

import dev.projectskins.skin.SkinSource;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;
import java.util.UUID;

/**
 * Одно серверное назначение скина. В пакет входит источник, а не изображение:
 * встроенный ресурс уже находится в моде клиента, URL загружается клиентом в фоне.
 * UUID не зависит от видимости сущности и переживает респавн. Положительный номер
 * изменения позволяет клиенту отвергнуть результат устаревшей загрузки.
 * Класс общий для обеих сторон и не ссылается на клиент Minecraft.
 */
public record SkinSyncPacket(UUID playerId, SkinSource source, long revision) {
    /** Верхняя граница строки проверяется до выделения памяти декодером. */
    private static final int MAX_SOURCE_LENGTH = 2048;

    public SkinSyncPacket {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(source, "source");
        if (revision <= 0) {
            throw new IllegalArgumentException("Номер изменения должен быть положительным");
        }
    }

    public static void encode(SkinSyncPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.playerId());
        buffer.writeByte(packet.source().type() == SkinSource.Type.LOCAL ? 0 : 1);
        buffer.writeUtf(packet.source().value(), MAX_SOURCE_LENGTH);
        buffer.writeVarLong(packet.revision());
    }

    /** Декодирование проверяет тип и источник, но не обращается к игровому состоянию. */
    public static SkinSyncPacket decode(FriendlyByteBuf buffer) {
        UUID playerId = buffer.readUUID();
        SkinSource.Type type = switch (buffer.readUnsignedByte()) {
            case 0 -> SkinSource.Type.LOCAL;
            case 1 -> SkinSource.Type.URL;
            default -> throw new IllegalArgumentException("Неизвестный тип источника скина");
        };
        SkinSource source = new SkinSource(type, buffer.readUtf(MAX_SOURCE_LENGTH));
        return new SkinSyncPacket(playerId, source, buffer.readVarLong());
    }
}
