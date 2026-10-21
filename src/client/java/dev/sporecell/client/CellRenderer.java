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
    private static final ModelPart CUBE=createCube();
    private static ModelPart createCube() {
        ModelData data=new ModelData();data.getRoot().addChild("cube",ModelPartBuilder.create().uv(0,0).cuboid(-8,0,-8,16,16,16),ModelTransform.NONE);
        return TexturedModelData.of(data,16,16).createModel().getChild("cube");
    }
    public CellRenderer(EntityRendererFactory.Context context) {super(context);shadowRadius=.4f;}
    @Override public Identifier getTexture(CellEntity entity) { return WHITE; }
    @Override public boolean shouldRender(CellEntity e,Frustum frustum,double x,double y,double z) {
        var client=net.minecraft.client.MinecraftClient.getInstance();
        if(CellClient.active && client.getCameraEntity() instanceof CellEntity controlled && controlled!=e && !CellClient.specs.isEmpty()) {
            double sight=CellClient.body.stats(CellClient.specs).sight()*controlled.size()+e.radius();
            if(e.kind()!=CellEntity.BACKGROUND && controlled.squaredDistanceTo(e)>sight*sight)return false;
        }
        return super.shouldRender(e,frustum,x,y,z);
    }
    private static void box(MatrixStack matrices,VertexConsumer vertices,int light,int color,float x,float y,float z,float sx,float sy,float sz) {
        matrices.push();matrices.translate(x,y,z);matrices.scale(sx,sy,sz);
        CUBE.render(matrices,vertices,light,OverlayTexture.DEFAULT_UV,0xFF000000|color);matrices.pop();
    }
    @Override public void render(CellEntity e,float yaw,float tickDelta,MatrixStack m,VertexConsumerProvider provider,int light) {
        VertexConsumer v=provider.getBuffer(RenderLayer.getEntitySolid(WHITE));m.push();
        float size=(float)e.size();m.scale(size,size,size);
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
        if(e.kind()==CellEntity.EGG){
            box(m,v,light,0xDBE9C6,0,0,0,.8f,.65f,.8f);
            box(m,v,light,0x9BC7AA,0,.65f,0,.55f,.24f,.55f);
        } else if(e.kind()==CellEntity.ROCK) {
            box(m,v,light,0x54877C,0,-.3f,0,1.6f,1.1f,1.4f);
            box(m,v,light,0x74A094,.35f,.6f,-.2f,.85f,.5f,.9f);
            box(m,v,light,0x97B493,-.4f,.7f,.1f,.35f,.23f,.35f);
        } else if(e.food()) {
            box(m,v,light,e.kind()==CellEntity.PLANT?0x92D653:0xEF897B,0,.05f,0,.25f,.23f,.25f);
            box(m,v,light,e.kind()==CellEntity.PLANT?0xC6F27D:0xFFC6AB,.1f,.23f,0,.13f,.1f,.13f);
        } else renderPieces(e.blueprint(),e.kind(),e.owner==null,e.getUuid().hashCode(),e.age+tickDelta,m,provider,light,e.age-e.lastHurtAge<7);
        m.pop();
        if(!e.food()&&!e.scenery()&&(e.health()<6||e.getId()==CellClient.targetId))healthBar(e,m,provider,light);
        super.render(e,yaw,tickDelta,m,provider,light);
    }
    private void healthBar(CellEntity e,MatrixStack matrices,VertexConsumerProvider provider,int light){
        matrices.push();matrices.translate(0,1.3*e.size(),0);matrices.multiply(dispatcher.getRotation());
        float scale=(float)(.027*Math.sqrt(e.size()));matrices.scale(scale,-scale,scale);
        var matrix=matrices.peek().getPositionMatrix();var v=provider.getBuffer(RenderLayer.getDebugQuads());
        bar(v,matrix,-18,-2,18,3,0xE0253642);
        bar(v,matrix,-18,-2,-18+36*Math.clamp(e.health()/6,0,1),3,0xFFEF8679);
        String text=String.format(java.util.Locale.ROOT,"%.0f/6",Math.max(0,e.health()));
        getTextRenderer().draw(text,-getTextRenderer().getWidth(text)/2f,-13,0xFFE8EDDA,false,matrix,provider,net.minecraft.client.font.TextRenderer.TextLayerType.NORMAL,0,light);
        if(e.age-e.lastHurtAge<20){
            String damage=String.format(java.util.Locale.ROOT,"−%.0f",e.lastDamage);
            getTextRenderer().draw(damage,-8,-24-(e.age-e.lastHurtAge),0xFFFFBA8E,false,matrix,provider,net.minecraft.client.font.TextRenderer.TextLayerType.NORMAL,0,light);
        }
        matrices.pop();
    }
    private static void bar(VertexConsumer v,org.joml.Matrix4f m,float x,float y,float bx,float by,int color){
        v.vertex(m,x,y,0).color(color);v.vertex(m,x,by,0).color(color);v.vertex(m,bx,by,0).color(color);v.vertex(m,bx,y,0).color(color);
    }
    public static void renderDesign(Blueprint blueprint,MatrixStack matrices,VertexConsumerProvider provider) {
        renderPieces(blueprint,CellEntity.PLAYER,false,0,0,matrices,provider,0xF000F0,false);
    }
    private static void renderPieces(Blueprint blueprint,int kind,boolean npc,int seed,float age,MatrixStack m,VertexConsumerProvider provider,int light,boolean hurt) {
        VertexConsumer v=provider.getBuffer(RenderLayer.getEntitySolid(WHITE));
        for(Blueprint.Piece p:blueprint.pieces()) {
            PartSpec spec=CellClient.specs.get(p.part());int color=spec==null?0x71D9B5:spec.color();
            if(p.part()==Part.BODY||p.part()==Part.CORE) {
                int[] palette=kind==CellEntity.HUNTER?new int[]{0xBB667B,0xD08A63,0x996AAF}:new int[]{0x67BDB5,0x91B969,0xAB86BA,0xCCAC6B};
                if(npc && kind!=CellEntity.PLAYER)color=palette[Math.floorMod(seed,palette.length)];
            }
            if(kind==CellEntity.BACKGROUND)color=0x386C7C;
            if(kind==CellEntity.MATE)color=0xD59ABA;
            if(hurt)color=0xEF625F;
            float x=(float)p.px(),z=(float)p.pz();
            if(!p.mounted())box(m,v,light,color,x,0,z,.59f,p.part()==Part.ARMOR?.65f:.42f,.59f);
            m.push();m.translate(x,p.py(),z);m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float)-p.angle()));
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float)p.pitch()));
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float)p.roll()));
            m.scale((float)p.size(),(float)p.size(),(float)p.size());
            switch(p.part()) {
                case CORE -> {
                    box(m,v,light,0x317A80,0,.43f,0,.23f,.12f,.23f);
                    if(blueprint.pieces().stream().noneMatch(q->q.part()==Part.EYE))for(int side:new int[]{-1,1}) {
                        box(m,v,light,0xE7EED9,side*.17f,.43f,-.19f,.13f,.11f,.14f);
                        box(m,v,light,0x183A48,side*.17f,.55f,-.22f,.06f,.025f,.07f);
                    }
                }
                case EYE -> {
                    box(m,v,light,0xF5F4E9,0,.42f,0,.34f,.26f,.34f);
                    box(m,v,light,0x132F43,0,.69f,-.055f,.17f,.03f,.17f);
                }
                case HERBIVORE_MOUTH,CARNIVORE_MOUTH,OMNIVORE_MOUTH -> {
                    box(m,v,light,0x314656,-.17f,.43f,-.16f,.13f,.1f,.35f);
                    box(m,v,light,0x314656,.17f,.43f,-.16f,.13f,.1f,.35f);
                }
                case SPIKE -> {
                    box(m,v,light,color,0,.15f,-.36f,.28f,.26f,.42f);
                    box(m,v,light,0xF0F4EE,0,.21f,-.64f,.12f,.12f,.22f);
                }
                case FLAGELLUM -> {
                    float wave=(float)Math.sin(age*.4)*.12f;
                    for(int i=0;i<4;i++)box(m,v,light,color,wave*i*.4f,.1f,-.38f-i*.16f,.12f,.14f,.18f);
                }
                case CILIA -> { for(int i=-1;i<=1;i++)box(m,v,light,color,i*.17f,.1f,-.4f,.07f,.14f,.3f); }
                default -> {}
            }
            m.pop();
        }
    }

}
