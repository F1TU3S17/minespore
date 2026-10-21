package dev.sporecell.test.mixin;

import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Mouse.class)
public interface MouseInputProbe {
    @Invoker("onCursorPos")
    void sporetest$cursor(long window,double x,double y);
    @Invoker("onMouseButton")
    void sporetest$button(long window,int button,int action,int modifiers);
}
