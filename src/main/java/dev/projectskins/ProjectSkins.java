package dev.projectskins;

import com.mojang.logging.LogUtils;
import dev.projectskins.client.ClientBootstrap;
import dev.projectskins.network.ProjectSkinsNetwork;
import dev.projectskins.server.ServerSkinEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Общая точка входа ProjectSkins для Forge 1.20.1.
 * Регистрирует канал на обеих физических сторонах и серверные события.
 * Клиентская инициализация вынесена в отдельный класс и отложенную ссылку:
 * выделенный сервер не загружает Minecraft, TextureManager и классы рендера.
 */
@Mod(ProjectSkins.MOD_ID)
public final class ProjectSkins {
    public static final String MOD_ID = "projectskins";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ProjectSkins() {
        ProjectSkinsNetwork.register();
        MinecraftForge.EVENT_BUS.register(new ServerSkinEvents());
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientBootstrap::initialize);
    }
}

