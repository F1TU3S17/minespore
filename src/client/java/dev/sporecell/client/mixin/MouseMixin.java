package dev.sporecell.client.mixin;

import dev.sporecell.client.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.lwjgl.glfw.GLFW;

@Mixin(Mouse.class)
public abstract class MouseMixin {
    @Shadow @Final private MinecraftClient client;
    @Inject(method="onMouseButton",at=@At("HEAD"),cancellable=true)
    private void sporecell$steer(long window,int button,int action,int mods,CallbackInfo ci){
        if(window==client.getWindow().getHandle()&&CellClient.active&&client.currentScreen==null&&button==GLFW.GLFW_MOUSE_BUTTON_LEFT){
            MouseSteering.held=action!=GLFW.GLFW_RELEASE;ci.cancel();
        }
    }
    @Inject(method="lockCursor",at=@At("HEAD"),cancellable=true)
    private void sporecell$keepCursor(CallbackInfo ci){if(CellClient.active)ci.cancel();}
}
