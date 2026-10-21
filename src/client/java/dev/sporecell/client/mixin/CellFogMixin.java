package dev.sporecell.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.sporecell.CellEntity;
import dev.sporecell.client.CellClient;
import net.minecraft.client.render.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BackgroundRenderer.class)
public abstract class CellFogMixin {
    @Inject(method="applyFog",at=@At("TAIL"))
    private static void sporecell$scaledFog(Camera camera,BackgroundRenderer.FogType type,float distance,boolean thick,float delta,CallbackInfo ci){
        if(CellClient.active&&camera.getFocusedEntity() instanceof CellEntity cell){
            RenderSystem.setShaderFogStart((float)(64*cell.size()));
            RenderSystem.setShaderFogEnd((float)(96*cell.size()));
        }
    }
}
