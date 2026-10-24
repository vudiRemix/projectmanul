package dev.projectskins.client;

import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Граница жизненного цикла клиентской сессии.
 * При отключении освобождает собственные GPU-текстуры и инвалидирует загрузки:
 * результат старого сервера не должен изменить игрока на следующем сервере.
 * Смена измерения и респавн не являются отключением и не очищают назначения.
 */
public final class ClientSkinEvents {
    @SubscribeEvent
    public void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientSkinCache.getInstance().reset();
    }
}

