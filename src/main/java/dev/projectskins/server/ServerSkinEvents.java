package dev.projectskins.server;

import dev.projectskins.command.ProjectSkinsCommand;
import dev.projectskins.network.ProjectSkinsNetwork;
import dev.projectskins.network.SkinSyncPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * События логического сервера, зарегистрированные на MinecraftForge.EVENT_BUS.
 * При входе отправляется полный снимок, чтобы поздний участник видел скины,
 * назначенные до его подключения. Пакеты отправляются на серверном потоке;
 * команды не могут вклиниться в середину формирования снимка.
 * Респавн/измерение не очищают реестр: назначения хранятся по UUID.
 */
public final class ServerSkinEvents {
    private final ServerSkinRegistry registry = new ServerSkinRegistry();
    private LocalSkinCatalog catalog;

    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        if (catalog == null) {
            catalog = new LocalSkinCatalog();
        }
        ProjectSkinsCommand.register(event.getDispatcher(), registry, catalog);
    }

    @SubscribeEvent
    public void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ProjectSkinsNetwork.sendReset(player);
            for (SkinSyncPacket assignment : registry.snapshot()) {
                ProjectSkinsNetwork.sendTo(player, assignment);
            }
        }
    }

    @SubscribeEvent
    public void serverStopped(ServerStoppedEvent event) {
        // Обязательно и для встроенного сервера: следующий мир в том же JVM
        // не должен унаследовать назначения предыдущего мира.
        registry.clear();
    }
}
