package dev.sporecell.client;

import dev.sporecell.CellEntity;
import dev.sporecell.core.CellField;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.*;
import org.joml.Matrix4f;

/** Own procedural assets: a layered microscopic water volume, without world block walls. */
final class PondRenderer {
    static int renderedFrames;
    static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(context-> {
            if(!CellClient.active || !(context.camera().getFocusedEntity() instanceof CellEntity cell))return;
            var matrices=context.matrixStack();if(matrices==null||context.consumers()==null)return;
            var camera=context.camera().getPos();
            matrices.push();matrices.translate(-camera.x,-camera.y,-camera.z);
            var matrix=matrices.peek().getPositionMatrix();
            VertexConsumer vertices=context.consumers().getBuffer(RenderLayer.getDebugQuads());
            double scale=cell.size(),tile=12*scale;
            double originX=Math.floor(cell.getX()/tile)*tile,originZ=Math.floor(cell.getZ()/tile)*tile;
            float y=(float)(66.05-8*scale);
            double time=cell.age*.003;
            for(int x=-13;x<=13;x++)for(int z=-13;z<=13;z++) {
                float ax=(float)(originX+x*tile),az=(float)(originZ+z*tile),bx=(float)(ax+tile),bz=(float)(az+tile);
                water(vertices,matrix,ax,y,az,time);water(vertices,matrix,ax,y,bz,time);
                water(vertices,matrix,bx,y,bz,time);water(vertices,matrix,bx,y,az,time);
            }
            // Suspended voxel motes and square bubbles lie at several depths, producing parallax.
            double spacing=5*scale;
            double span=90*scale;
            for(int i=0;i<240;i++) {
                long hash=i*73428767L;
                float px=(float)(cell.getX()+CellField.wrap((i*37%197)*spacing-cell.getX(),-span/2,span/2));
                float pz=(float)(cell.getZ()+CellField.wrap((i*83%211)*spacing-cell.getZ(),-span/2,span/2));
                float py=(float)(66.05-(1+Math.floorMod(hash,5))*.65*scale);
                float radius=(float)((.04+Math.floorMod(hash,8)*.014)*scale);
                float drift=(float)(Math.sin(time+hash)*scale*.4);
                quad(vertices,matrix,px+drift-radius,py,pz-radius,px+drift+radius,pz+radius,0x508DD6CF);
                if((hash&12)==0) {
                    float r=radius*3,t=r*.16f;
                    quad(vertices,matrix,px-r,py,pz-r,px+r,pz-r+t,0x407ED7D4);
                    quad(vertices,matrix,px-r,py,pz+r-t,px+r,pz+r,0x407ED7D4);
                    quad(vertices,matrix,px-r,py,pz-r,px-r+t,pz+r,0x407ED7D4);
                    quad(vertices,matrix,px+r-t,py,pz-r,px+r,pz+r,0x407ED7D4);
                }
            }
            matrices.pop();renderedFrames++;
        });
    }
    private static void water(VertexConsumer v,Matrix4f m,float x,float y,float z,double time) {
        double wave=Math.sin(x*.028+time)*Math.cos(z*.023-time*.7)*.5+.5;
        int r=(int)(17+wave*11),g=(int)(73+wave*20),b=(int)(92+wave*24);
        v.vertex(m,x,y,z).color(r,g,b,255);
    }
    private static void quad(VertexConsumer v,Matrix4f m,float x,float y,float z,float bx,float bz,int color) {
        v.vertex(m,x,y,z).color(color);v.vertex(m,x,y,bz).color(color);
        v.vertex(m,bx,y,bz).color(color);v.vertex(m,bx,y,z).color(color);
    }
}
