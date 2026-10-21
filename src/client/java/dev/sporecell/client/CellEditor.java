package dev.sporecell.client;

import com.google.gson.JsonObject;
import dev.sporecell.*;
import dev.sporecell.core.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.RotationAxis;
import org.lwjgl.glfw.GLFW;
import java.util.*;

public final class CellEditor extends Screen {
    private final List<Blueprint.Piece> pieces=new ArrayList<>(CellClient.body.pieces());
    private final Blueprint original=CellClient.body;
    private final int revision=CellClient.revision;
    private final Deque<List<Blueprint.Piece>> undo=new ArrayDeque<>();
    private Part selected=Part.BODY;
    private int direction,gridX,gridY,step,paletteStep;
    private boolean symmetry;
    private String error="";
    private ButtonWidget apply;
    private CellEntity preview;
    static final Map<Part,String> NAMES=Map.ofEntries(
        Map.entry(Part.CORE,"Ядро"),Map.entry(Part.BODY,"Тело"),Map.entry(Part.FLAGELLUM,"Жгутик"),
        Map.entry(Part.CILIA,"Реснички"),Map.entry(Part.EYE,"Глаз"),Map.entry(Part.HERBIVORE_MOUTH,"Фильтратор"),
        Map.entry(Part.CARNIVORE_MOUTH,"Челюсти"),Map.entry(Part.OMNIVORE_MOUTH,"Хоботок"),Map.entry(Part.SPIKE,"Шип"),Map.entry(Part.ARMOR,"Оболочка"));
    private static final Map<Part,String> HELP=Map.ofEntries(
        Map.entry(Part.BODY,"Меняет форму тела. Большая клетка движется медленнее."),
        Map.entry(Part.FLAGELLUM,"Увеличивает скорость. Направление установки влияет на тягу."),
        Map.entry(Part.CILIA,"Ускоряют поворот. Боковое размещение эффективнее."),
        Map.entry(Part.EYE,"Увеличивает радиус видимости пищи и других клеток."),
        Map.entry(Part.HERBIVORE_MOUTH,"Ест зелёную растительную пищу перед ртом."),
        Map.entry(Part.CARNIVORE_MOUTH,"Кусает противника и ест розовую животную пищу."),
        Map.entry(Part.OMNIVORE_MOUTH,"Всеядность: растения и мясо. Открывается при прогрессе 100."),
        Map.entry(Part.SPIKE,"Контактная атака в направлении шипа."),
        Map.entry(Part.ARMOR,"Утолщённый кубический сегмент. Запас здоровья остаётся 6."));
    public CellEditor() {super(Text.literal("Редактор клетки"));}
    Blueprint draft() {return new Blueprint(pieces);}
    @Override public void renderBackground(DrawContext draw,int mouseX,int mouseY,float delta) {}
    @Override protected void init() {
        step=Math.max(8,Math.min(27,Math.min((height-96)/9,(width-270)/9)));gridX=132;gridY=46;
        paletteStep=Math.max(12,Math.min(23,(height-108)/10));int y=44;
        for(Part part:Part.values()) {
            if(part==Part.CORE)continue;
            addDrawableChild(ButtonWidget.builder(Text.literal(NAMES.get(part)+" ("+CellClient.specs.get(part).cost()+")"),b->selected=part)
                .dimensions(8,y,115,Math.min(20,paletteStep-2)).build());y+=paletteStep;
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Поворот [R]"),b->direction=(direction+1)%4).dimensions(8,y+2,115,18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Симметрия [S]"),b->symmetry=!symmetry).dimensions(8,y+22,115,18).build());
        apply=addDrawableChild(ButtonWidget.builder(Text.literal("Сохранить"),b->apply()).dimensions(width/2-106,height-29,100,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Отмена"),b->close()).dimensions(width/2+6,height-29,100,20).build());
    }
    private void apply() {
        try {
            Blueprint next=draft();next.validate();
            if(!apply.active)return;
            JsonObject obj=new JsonObject();obj.addProperty("type","apply");obj.addProperty("revision",revision);
            obj.add("body",SporeCell.GSON.toJsonTree(next));CellClient.send(obj);
        } catch(RuntimeException ex) {error=ex.getMessage();}
    }
    @Override public void render(DrawContext draw,int mx,int my,float delta) {
        draw.fill(0,0,width,height,0xFF102A35);
        draw.drawCenteredTextWithShadow(textRenderer,"РЕДАКТОР КЛЕТКИ / ДНК "+CellClient.dna,width/2,12,0xD4F5E5);
        draw.drawCenteredTextWithShadow(textRenderer,"ЛКМ: поставить · ПКМ: убрать · R: поворот · Z: отменить",width/2,29,0xA7BFCA);
        for(int z=-4;z<=4;z++)for(int x=-4;x<=4;x++) {
            int px=gridX+(x+4)*step,py=gridY+(z+4)*step;
            draw.fill(px,py,px+step-1,py+step-1,(x+z)%2==0?0xFF244451:0xFF294B57);
        }
        for(Blueprint.Piece p:pieces) {
            int px=gridX+(p.x()+4)*step,py=gridY+(p.z()+4)*step;
            draw.fill(px+2,py+2,px+step-3,py+step-3,0xFF000000|CellClient.specs.get(p.part()).color());
            draw.fill(px+2,py+2,px+step-3,py+5,0x55FFFFFF);
            draw.fill(px+step-6,py+5,px+step-3,py+step-3,0x55000000);
            draw.drawCenteredTextWithShadow(textRenderer,p.part()==Part.CORE?"+":new String[]{"^",">","v","<"}[p.direction()],px+step/2,py+step/2-3,0xFFFFFF);
        }
        int sx=Math.min(width-140,gridX+step*9+12),sy=47;
        draw.drawText(textRenderer,"Выбрано: "+NAMES.get(selected),sx,sy,0xFFFFFF,false);
        draw.drawText(textRenderer,"Поворот: "+direction*90+(symmetry?" · симм.":""),sx,sy+14,0xA7BFCA,false);
        try {
            Blueprint b=draft();Blueprint.Stats s=b.stats(CellClient.specs);int cost=Blueprint.purchaseCost(original,b,CellClient.specs);
            draw.drawText(textRenderer,"Расход ДНК: "+cost,sx,sy+32,cost>CellClient.dna?0xFF8888:0x9DE2AB,false);
            draw.drawText(textRenderer,"Остаток: "+(CellClient.dna-cost),sx,sy+46,0xFFFFFF,false);
            String food=s.herbivore()&&s.carnivore()?"растения и мясо":s.herbivore()?"растения":s.carnivore()?"мясо":"нет рта";
            String[] lines={"Здоровье: 6 · "+pieces.size()+"/49",
                String.format(Locale.ROOT,"Скорость: %.2f",s.speed()),
                String.format(Locale.ROOT,"Поворот: %.0f · Обзор: %.0f",s.turn(),s.sight()),"Пища: "+food};
            for(int i=0;i<lines.length;i++)draw.drawText(textRenderer,lines[i],sx,sy+66+i*12,0xD3E7E9,false);
            apply.active=cost<=CellClient.dna&&(CellClient.progress>=100||pieces.stream().noneMatch(p->p.part()==Part.OMNIVORE_MOUTH));
            if(client.world!=null) {
                if(preview==null)preview=new CellEntity(SporeCell.CELL,client.world);
                preview.blueprint(b);
                var matrices=draw.getMatrices();matrices.push();
                int extent=pieces.stream().mapToInt(p->Math.max(Math.abs(p.x()),Math.abs(p.z()))).max().orElse(1);
                int size=Math.max(6,Math.min(25,(height-195)/(extent*2+1)));
                matrices.translate(sx+65,height-65,150);matrices.scale(size,-size,size);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(35));matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(35));
                client.getEntityRenderDispatcher().getRenderer(preview).render(preview,0,delta,matrices,draw.getVertexConsumers(),0xF000F0);
                draw.draw();matrices.pop();
            }
        } catch(RuntimeException ex) {apply.active=false;draw.drawTextWrapped(textRenderer,Text.literal(ex.getMessage()),sx,sy+40,130,0xFF9A8B);}
        String status=error.isEmpty()?CellClient.message:error;
        if(!status.isEmpty())draw.drawCenteredTextWithShadow(textRenderer,status,width/2,height-43,0xFFD594);
        super.render(draw,mx,my,delta);
        for(int i=1;i<Part.values().length;i++) {
            Part p=Part.values()[i];int y=44+(i-1)*paletteStep;
            if(p==selected)draw.fill(5,y,8,y+Math.min(20,paletteStep-2),0xFFFFFF66);
            // A tiny, recognisable illustration accompanies each palette entry.
            icon(draw,p,113,y+2);

            if(mx>=8&&mx<123&&my>=y&&my<y+paletteStep)
                draw.drawOrderedTooltip(textRenderer,textRenderer.wrapLines(Text.literal(HELP.get(p)),190),mx,my);
        }
    }
    private void icon(DrawContext draw,Part part,int x,int y) {
        int color=0xFF000000|CellClient.specs.get(part).color();
        draw.fill(x,y+3,x+7,y+10,color);
        switch(part) {
            case EYE -> {draw.fill(x,y+2,x+7,y+8,0xFFF5F4E9);draw.fill(x+2,y+3,x+5,y+6,0xFF102A35);}
            case FLAGELLUM -> {for(int i=0;i<4;i++)draw.fill(x+2+i%2,y+10+i*2,x+4+i%2,y+12+i*2,color);}
            case CILIA -> {for(int i=0;i<3;i++)draw.fill(x+i*3,y-1,x+i*3+1,y+4,color);}
            case SPIKE -> {draw.fill(x+2,y,x+5,y+5,color);draw.fill(x+3,y-2,x+4,y+1,0xFFFFFFFF);}
            case HERBIVORE_MOUTH,CARNIVORE_MOUTH,OMNIVORE_MOUTH -> {draw.fill(x+2,y,x+5,y+6,0xFF102A35);}
            default -> draw.fill(x,y+3,x+7,y+5,0x55FFFFFF);
        }
    }
    private void place(int x,int z,int button,int rotation) {
        pieces.removeIf(p->p.x()==x&&p.z()==z);
        if(button==0)pieces.add(new Blueprint.Piece(x,z,selected,rotation));
    }
    @Override public boolean mouseClicked(double mx,double my,int button) {
        int x=(int)Math.floor((mx-gridX)/step)-4,z=(int)Math.floor((my-gridY)/step)-4;
        if(Math.abs(x)<=4&&Math.abs(z)<=4&&(button==0||button==1)) {
            if(x==0&&z==0)return true;undo.push(List.copyOf(pieces));place(x,z,button,direction);
            if(symmetry&&x!=0)place(-x,z,button,(4-direction)%4);error="";return true;
        }
        return super.mouseClicked(mx,my,button);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        if(key==GLFW.GLFW_KEY_R) {direction=(direction+1)%4;return true;}
        if(key==GLFW.GLFW_KEY_S) {symmetry=!symmetry;return true;}
        if(key==GLFW.GLFW_KEY_Z&&!undo.isEmpty()) {pieces.clear();pieces.addAll(undo.pop());return true;}
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public void close() {CellClient.send("close");super.close();}
    @Override public boolean shouldPause() {return false;}
}
