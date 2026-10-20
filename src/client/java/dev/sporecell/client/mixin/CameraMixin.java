package dev.sporecell.client.mixin;

import dev.sporecell.CellEntity;
import dev.sporecell.client.CellClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow private boolean thirdPerson;
    @Shadow protected abstract void setPos(double x,double y,double z);
    @Shadow protected abstract void setRotation(float yaw,float pitch);
    @Inject(method="update",at=@At("TAIL"))
    private void sporecell$follow(BlockView area,Entity focused,boolean thirdPerson,boolean inverse,float delta,CallbackInfo ci) {
        if(CellClient.active && focused instanceof CellEntity) {
            double x=focused.prevX+(focused.getX()-focused.prevX)*delta;
            double z=focused.prevZ+(focused.getZ()-focused.prevZ)*delta;
            this.thirdPerson=true;setRotation(0,70);setPos(x,focused.getY()+14,z-5.1);
        }
    }
}
