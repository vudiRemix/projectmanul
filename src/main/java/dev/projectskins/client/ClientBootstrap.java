package dev.projectskins.client;

import dev.projectskins.network.ProjectSkinsNetwork;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Инициализация, вызываемая исключительно на физическом клиенте.
 * Общий сетевой канал получает обработчики через интерфейсы Java и поэтому
 * не ссылается на Minecraft при запуске выделенного сервера.
 */
public final class ClientBootstrap {
    private ClientBootstrap() { }

    public static void initialize() {
        ProjectSkinsNetwork.installClientHandlers(
                packet -> ClientSkinCache.getInstance().apply(packet),
                () -> ClientSkinCache.getInstance().reset());
        MinecraftForge.EVENT_BUS.register(new ClientSkinEvents());
        FMLJavaModLoadingContext.get().getModEventBus().addListener(ClientBootstrap::registerReloadListener);
    }

    private static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener)
                manager -> ClientSkinCache.getInstance().reload());
    }
}

