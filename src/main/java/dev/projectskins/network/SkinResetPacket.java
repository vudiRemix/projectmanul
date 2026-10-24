package dev.projectskins.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Начало полного снимка при подключении. Клиент очищает назначения и повышает
 * идентификатор своей сессии: незавершённые загрузки предыдущей сессии больше
 * не имеют права применять текстуры. После этого пакета идут SkinSyncPacket
 * по одному на назначение. Такое представление не создаёт огромный сетевой пакет.
 * Порядок сохраняется одним соединением Minecraft.
 */
public record SkinResetPacket() {
    public static void encode(SkinResetPacket packet, FriendlyByteBuf buffer) {
        // Полезной нагрузки нет: само получение пакета означает сброс.
    }

    public static SkinResetPacket decode(FriendlyByteBuf buffer) {
        return new SkinResetPacket();
    }
}
