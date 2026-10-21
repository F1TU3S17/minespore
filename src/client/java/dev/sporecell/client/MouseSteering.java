package dev.sporecell.client;

import dev.sporecell.CellEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

/** A world-plane ray from the unlocked mouse cursor, independent of GUI scale. */
public final class MouseSteering {
    public static boolean held;
    private MouseSteering(){}
    public static Vec3d direction(MinecraftClient client,CellEntity cell){
        double nx=(client.mouse.getX()/client.getWindow().getWidth()-.5)*2;
        double ny=(.5-client.mouse.getY()/client.getWindow().getHeight())*2;
        double tangent=Math.tan(Math.toRadians(client.options.getFov().getValue()/2d));
        double aspect=(double)client.getWindow().getWidth()/client.getWindow().getHeight();
        double pitch=Math.toRadians(70);
        Vec3d ray=new Vec3d(nx*tangent*aspect,-Math.sin(pitch)+ny*tangent*Math.cos(pitch),-Math.cos(pitch)-ny*tangent*Math.sin(pitch)).normalize();
        Vec3d camera=client.gameRenderer.getCamera().getPos();
        if(ray.y>=-.001)return Vec3d.ZERO;
        Vec3d aim=camera.add(ray.multiply((cell.getY()-camera.y)/ray.y));
        Vec3d delta=new Vec3d(aim.x-cell.getX(),0,aim.z-cell.getZ());
        double distance=delta.length();
        if(distance<.35*cell.size())return Vec3d.ZERO;
        return delta.normalize().multiply(Math.min(1,distance/(2*cell.size())));
    }
}
