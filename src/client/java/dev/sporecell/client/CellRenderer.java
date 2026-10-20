package dev.sporecell.client;

import dev.sporecell.*;
import dev.sporecell.core.*;
import net.minecraft.client.model.*;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

public final class CellRenderer extends EntityRenderer<CellEntity> {
    private static final Identifier WHITE=Identifier.of("sporecell","textures/entity/white.png");
    private final ModelPart cube;
    public CellRenderer(EntityRendererFactory.Context context) {
        super(context);shadowRadius=.4f;
        ModelData data=new ModelData();data.getRoot().addChild("cube",ModelPartBuilder.create().uv(0,0).cuboid(-8,0,-8,16,16,16),ModelTransform.NONE);
        cube=TexturedModelData.of(data,16,16).createModel().getChild("cube");
    }
    @Override public Identifier getTexture(CellEntity entity) { return WHITE; }
    @Override public boolean shouldRender(CellEntity e,Frustum frustum,double x,double y,double z) {
        var client=net.minecraft.client.MinecraftClient.getInstance();
        if(CellClient.active && client.getCameraEntity() instanceof CellEntity controlled && controlled!=e && !CellClient.specs.isEmpty()) {
            double sight=CellClient.body.stats(CellClient.specs).sight();
            if(controlled.squaredDistanceTo(e)>sight*sight)return false;
        }
        return super.shouldRender(e,frustum,x,y,z);
    }
    private void box(MatrixStack matrices,VertexConsumer vertices,int light,int color,float x,float y,float z,float sx,float sy,float sz) {
        matrices.push();matrices.translate(x,y,z);matrices.scale(sx,sy,sz);
        cube.render(matrices,vertices,light,OverlayTexture.DEFAULT_UV,0xFF000000|color);matrices.pop();
    }
    @Override public void render(CellEntity e,float yaw,float tickDelta,MatrixStack m,VertexConsumerProvider provider,int light) {
        VertexConsumer v=provider.getBuffer(RenderLayer.getEntitySolid(WHITE));m.push();
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
        if(e.food()) {
            box(m,v,light,e.kind()==CellEntity.PLANT?0x92D653:0xEF897B,0,.05f,0,.25f,.23f,.25f);
            box(m,v,light,e.kind()==CellEntity.PLANT?0xC6F27D:0xFFC6AB,.1f,.23f,0,.13f,.1f,.13f);
        } else for(Blueprint.Piece p:e.blueprint().pieces()) {
            PartSpec spec=CellClient.specs.get(p.part());int color=spec==null?0x71D9B5:spec.color();
            if(e.kind()==CellEntity.HUNTER && p.part()==Part.CORE)color=0xBB667B;
            float x=p.x()*.65f,z=p.z()*.65f;
            box(m,v,light,color,x,0,z,.59f,p.part()==Part.ARMOR?.65f:.42f,.59f);
            m.push();m.translate(x,0,z);m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-p.direction()*90));
            switch(p.part()) {
                case CORE -> box(m,v,light,0x317A80,0,.43f,0,.23f,.12f,.23f);
                case EYE -> {
                    box(m,v,light,0xF5F4E9,0,.42f,0,.34f,.26f,.34f);
                    box(m,v,light,0x132F43,0,.69f,-.055f,.17f,.03f,.17f);
                }
                case HERBIVORE_MOUTH,CARNIVORE_MOUTH -> {
                    box(m,v,light,0x314656,-.17f,.43f,-.16f,.13f,.1f,.35f);
                    box(m,v,light,0x314656,.17f,.43f,-.16f,.13f,.1f,.35f);
                }
                case SPIKE -> {
                    box(m,v,light,color,0,.15f,-.36f,.28f,.26f,.42f);
                    box(m,v,light,0xF0F4EE,0,.21f,-.64f,.12f,.12f,.22f);
                }
                case FLAGELLUM -> {
                    float wave=(float)Math.sin((e.age+tickDelta)*.4)*.12f;
                    for(int i=0;i<4;i++)box(m,v,light,color,wave*i*.4f,.1f,-.38f-i*.16f,.12f,.14f,.18f);
                }
                case CILIA -> { for(int i=-1;i<=1;i++)box(m,v,light,color,i*.17f,.1f,-.4f,.07f,.14f,.3f); }
                default -> {}
            }
            m.pop();
        }
        m.pop();super.render(e,yaw,tickDelta,m,provider,light);
    }
}
