package dev.sporecell.client;

import com.google.gson.JsonObject;
import dev.sporecell.SporeCell;
import dev.sporecell.core.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import java.util.*;

public final class CellEditor extends Screen {
    private final List<Blueprint.Piece> pieces=new ArrayList<>(CellClient.body.pieces());
    private final Blueprint original=CellClient.body;
    private final int revision=CellClient.revision;
    private Part selected=Part.BODY;
    private int direction,gridX,gridY,step;
    private String error="";
    private ButtonWidget apply;
    private static final Map<Part,String> NAMES=Map.ofEntries(
            Map.entry(Part.CORE,"Core"),Map.entry(Part.BODY,"Body"),Map.entry(Part.FLAGELLUM,"Flagellum"),
            Map.entry(Part.CILIA,"Cilia"),Map.entry(Part.EYE,"Eye"),Map.entry(Part.HERBIVORE_MOUTH,"Plant mouth"),
            Map.entry(Part.CARNIVORE_MOUTH,"Carnivore mouth"),Map.entry(Part.SPIKE,"Spike"),Map.entry(Part.ARMOR,"Armor"));
    public CellEditor() { super(Text.literal("Cell editor")); }
    Blueprint draft() { return new Blueprint(pieces); }
    @Override public void renderBackground(DrawContext draw,int mouseX,int mouseY,float delta) {
        // This screen draws its own opaque background; vanilla blur would blur the grid and labels.
    }
    @Override protected void init() {
        step=Math.max(8,Math.min(27,Math.min((height-96)/9,(width-270)/9)));gridX=132;gridY=46;
        int y=44,paletteStep=Math.max(14,Math.min(23,(height-80)/9));
        for(Part p:Part.values()) {
            if(p==Part.CORE)continue;
            PartSpec spec=CellClient.specs.get(p);
            addDrawableChild(ButtonWidget.builder(Text.literal(NAMES.get(p)+" ("+spec.cost()+")"),b->selected=p)
                    .dimensions(8,y,115,Math.min(20,paletteStep-2)).build());y+=paletteStep;
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Rotate [R]"),b->direction=(direction+1)%4).dimensions(8,y+3,115,20).build());
        apply=addDrawableChild(ButtonWidget.builder(Text.literal("Apply"),b->apply()).dimensions(width/2-106,height-29,100,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"),b->close()).dimensions(width/2+6,height-29,100,20).build());
    }
    private void apply() {
        try {
            Blueprint next=draft();next.validate();
            if(Blueprint.purchaseCost(original,next,CellClient.specs)>CellClient.dna)return;
            JsonObject obj=new JsonObject();obj.addProperty("type","apply");obj.addProperty("revision",revision);
            obj.add("body",SporeCell.GSON.toJsonTree(next));CellClient.send(obj);
        } catch(RuntimeException ex) { error=ex.getMessage(); }
    }
    @Override public void render(DrawContext draw,int mouseX,int mouseY,float delta) {
        draw.fill(0,0,width,height,0xFA102A35);
        draw.drawCenteredTextWithShadow(textRenderer,"CELL EDITOR  /  DNA "+CellClient.dna,width/2,12,0xD4F5E5);
        draw.drawCenteredTextWithShadow(textRenderer,"Left: place/replace   Right: remove   R: rotate   North = up",width/2,29,0xA7BFCA);
        for(int z=-4;z<=4;z++)for(int x=-4;x<=4;x++) {
            int px=gridX+(x+4)*step,py=gridY+(z+4)*step;
            draw.fill(px,py,px+step-1,py+step-1,(x+z)%2==0?0xFF244451:0xFF294B57);
        }
        for(Blueprint.Piece p:pieces) {
            int px=gridX+(p.x()+4)*step,py=gridY+(p.z()+4)*step;
            int color=0xFF000000|CellClient.specs.get(p.part()).color();
            draw.fill(px+2,py+2,px+step-3,py+step-3,color);
            // Raised top and dark right face give an immediate cubical preview.
            draw.fill(px+2,py+2,px+step-3,py+5,0x55FFFFFF);
            draw.fill(px+step-6,py+5,px+step-3,py+step-3,0x55000000);
            String mark=p.part()==Part.CORE?"+":new String[]{"^",">","v","<"}[p.direction()];
            draw.drawCenteredTextWithShadow(textRenderer,mark,px+step/2,py+step/2-3,0xFFFFFF);
            if(mouseX>=px&&mouseX<px+step&&mouseY>=py&&mouseY<py+step)
                draw.drawTooltip(textRenderer,Text.literal(NAMES.get(p.part())+" / "+p.direction()*90+" deg"),mouseX,mouseY);
        }
        int sx=Math.min(width-140,gridX+step*9+12),sy=47;
        draw.drawText(textRenderer,"Selected: "+NAMES.get(selected),sx,sy,0xFFFFFF,false);
        draw.drawText(textRenderer,"Rotation: "+direction*90,sx,sy+14,0xA7BFCA,false);
        try {
            Blueprint b=draft();Blueprint.Stats s=b.stats(CellClient.specs);
            int cost=Blueprint.purchaseCost(original,b,CellClient.specs);
            draw.drawText(textRenderer,"DNA change: "+(cost>0?"-":"+")+Math.abs(cost),sx,sy+36,cost>CellClient.dna?0xFF8888:0x9DE2AB,false);
            draw.drawText(textRenderer,"Remaining: "+(CellClient.dna-cost),sx,sy+50,0xFFFFFF,false);
            String[] lines={String.format(Locale.ROOT,"HP: %.0f",s.health()),String.format(Locale.ROOT,"Speed: %.2f",s.speed()),
                    String.format(Locale.ROOT,"Turn: %.1f",s.turn()),String.format(Locale.ROOT,"Sight: %.0f",s.sight()),
                    "Plants: "+(s.herbivore()?"yes":"no"),"Meat: "+(s.carnivore()?"yes":"no"),"Parts: "+pieces.size()+"/49"};
            for(int i=0;i<lines.length;i++)draw.drawText(textRenderer,lines[i],sx,sy+76+i*12,0xD3E7E9,false);
            apply.active=cost<=CellClient.dna;
        } catch(RuntimeException ex) {
            apply.active=false;draw.drawTextWrapped(textRenderer,Text.literal(ex.getMessage()),sx,sy+40,130,0xFF9A8B);
        }
        String status=error.isEmpty()?CellClient.message:error;
        if(!status.isEmpty())draw.drawCenteredTextWithShadow(textRenderer,status,width/2,height-45,0xFFD594);
        super.render(draw,mouseX,mouseY,delta);
    }
    @Override public boolean mouseClicked(double mx,double my,int button) {
        int x=(int)Math.floor((mx-gridX)/step)-4,z=(int)Math.floor((my-gridY)/step)-4;
        if(Math.abs(x)<=4&&Math.abs(z)<=4&&(button==0||button==1)) {
            if(x==0&&z==0)return true;
            pieces.removeIf(p->p.x()==x&&p.z()==z);
            if(button==0)pieces.add(new Blueprint.Piece(x,z,selected,direction));error="";return true;
        }
        return super.mouseClicked(mx,my,button);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        if(key==GLFW.GLFW_KEY_R) { direction=(direction+1)%4;return true; }
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public void close() { CellClient.send("close");super.close(); }
    @Override public boolean shouldPause() { return false; }
}
