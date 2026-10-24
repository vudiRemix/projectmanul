package dev.projectskins.mixin.client;

import dev.projectskins.client.ClientSkinCache;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Единственная точка вмешательства в рендер: результат запроса текстуры игрока.
 * Vanilla PlayerRenderer использует этот метод для тела, рук и рукавов;
 * TACZ 1.1.8 передаёт анимированные руки этому же PlayerRenderer.
 * Ни модели, ни события рук, ни матрицы здесь не изменяются.
 * RETURN сохраняет штатное вычисление и заменяет лишь готовый ResourceLocation.
 * cancellable относится к возвращаемому значению метода, а не к событию рендера.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
    @Inject(method = "getSkinTextureLocation", at = @At("RETURN"), cancellable = true)
    private void projectskins$texture(CallbackInfoReturnable<ResourceLocation> result) {
        AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
        ResourceLocation custom = ClientSkinCache.getInstance().textureFor(player.getUUID());
        if (custom != null) {
            result.setReturnValue(custom);
        }
    }
}
