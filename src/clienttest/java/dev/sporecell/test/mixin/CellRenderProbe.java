package dev.sporecell.test.mixin;

import dev.sporecell.CellEntity;
import dev.sporecell.client.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value=CellRenderer.class,remap=false)
public abstract class CellRenderProbe {
    @Inject(method="render(Ldev/sporecell/CellEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",at=@At("HEAD"),remap=false)
    private void rendered(CellEntity e,float yaw,float delta,MatrixStack m,VertexConsumerProvider v,int light,CallbackInfo ci) {
        if(MinecraftClient.getInstance().getCameraEntity()==e)WorldRenderTest.renderedFrames++;
    }
    @Inject(method="shouldRender(Ldev/sporecell/CellEntity;Lnet/minecraft/client/render/Frustum;DDD)Z",at=@At("RETURN"),remap=false)
    private void culled(CellEntity e,Frustum f,double x,double y,double z,CallbackInfoReturnable<Boolean> ci) {
        if(MinecraftClient.getInstance().getCameraEntity()==e && ci.getReturnValue())WorldRenderTest.cullingAccepted=true;
    }
}
