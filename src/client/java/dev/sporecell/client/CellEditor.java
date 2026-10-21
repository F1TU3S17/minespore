package dev.sporecell.client;

import com.google.gson.JsonObject;
import dev.sporecell.*;
import dev.sporecell.core.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Body voxels and surface-mounted parts share a saved design, but have independent transforms. */
public final class CellEditor extends Screen {
    private final List<Blueprint.Piece> pieces=new ArrayList<>(CellClient.body.pieces());
    private final Blueprint original=CellClient.body;
    private final int revision=CellClient.revision;
    private final Deque<List<Blueprint.Piece>> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    final EditorViewport view=new EditorViewport();
    private Part palette=Part.BODY;
    private boolean placing=true,symmetry;
    private int selected=-1,left,right,top,bottom,paletteStep;
    private double rotation,presetPitch,presetRoll,scale=1;
    private int rotationAxis;
    private String error="";
    private ButtonWidget apply,symmetryButton;
    private Blueprint.Piece pending;
    private List<Blueprint.Piece> gesture;
    static final Map<Part,String> NAMES=Map.ofEntries(
        Map.entry(Part.CORE,"Ядро"),Map.entry(Part.BODY,"Тело"),Map.entry(Part.FLAGELLUM,"Жгутик"),
        Map.entry(Part.CILIA,"Реснички"),Map.entry(Part.EYE,"Глаз"),Map.entry(Part.HERBIVORE_MOUTH,"Фильтратор"),
        Map.entry(Part.CARNIVORE_MOUTH,"Челюсти"),Map.entry(Part.OMNIVORE_MOUTH,"Хоботок"),Map.entry(Part.SPIKE,"Шип"),Map.entry(Part.ARMOR,"Оболочка"));
    public CellEditor(){super(Text.literal("Редактор организма"));}
    Blueprint draft(){return new Blueprint(pieces);}
    int selectedIndex(){return selected;}
    @Override public void renderBackground(DrawContext draw,int mx,int my,float delta){}
    @Override protected void init(){
        left=130;right=width-142;top=42;bottom=height-54;
        view.centerX=(left+right)/2f;view.centerY=(top+bottom)/2f+8;
        double extent=pieces.stream().mapToDouble(p->Math.max(Math.abs(p.px()),Math.abs(p.pz()))+1).max().orElse(2);
        view.zoom=(float)Math.max(12,Math.min(62,Math.min(right-left,bottom-top)/(extent*2.3)));
        paletteStep=Math.max(13,Math.min(23,(height-102)/10));int y=43;
        for(Part p:Part.values())if(p!=Part.CORE){
            addDrawableChild(ButtonWidget.builder(Text.literal("   "+NAMES.get(p)+" "+CellClient.specs.get(p).cost()),b->choose(p))
                .dimensions(7,y,116,Math.min(20,paletteStep-1)).build());y+=paletteStep;
        }
        symmetryButton=addDrawableChild(ButtonWidget.builder(Text.literal("Симметрия [S]"),b->symmetry=!symmetry).dimensions(7,y+3,116,18).build());
        int x=width-135;
        addDrawableChild(ButtonWidget.builder(Text.literal("Ось поворота: Y"),b->{rotationAxis=(rotationAxis+1)%3;b.setMessage(Text.literal("Ось поворота: "+new String[]{"Y","X","Z"}[rotationAxis]));}).dimensions(x,89,127,14).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("− размер"),b->resize(-.1)).dimensions(x,105,62,18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+ размер"),b->resize(.1)).dimensions(x+65,105,62,18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("−15°"),b->rotate(-15)).dimensions(x,128,62,18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+15°"),b->rotate(15)).dimensions(x+65,128,62,18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Убрать [Del]"),b->remove()).dimensions(x,151,127,18).build());
        apply=addDrawableChild(ButtonWidget.builder(Text.literal("Сохранить"),b->apply()).dimensions(width/2-106,height-28,100,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Отмена"),b->close()).dimensions(width/2+6,height-28,100,20).build());
    }
    void choose(Part p){palette=p;placing=true;selected=-1;rotation=presetPitch=presetRoll=0;scale=1;pending=null;}
    private void remember(){undo.push(List.copyOf(pieces));if(undo.size()>64)undo.removeLast();redo.clear();error="";}
    private boolean inside(double x,double y){return x>=left&&x<right&&y>=top&&y<bottom;}
    private boolean functional(){return palette!=Part.BODY&&palette!=Part.ARMOR&&palette!=Part.CORE;}
    private Blueprint.Piece candidate(double mx,double my){
        if(functional()){
            var hit=view.pick(pieces,mx,my,true);if(hit==null)return null;var anchor=pieces.get(hit.index());
            return Blueprint.Piece.attachment(anchor.x(),anchor.z(),palette,
                Math.clamp(hit.point().x-anchor.x()*.65,-.5,.5),Math.clamp(hit.point().y-.42,-.2,.6),
                Math.clamp(hit.point().z-anchor.z()*.65,-.5,.5),rotation,scale).orient(rotation,presetPitch,presetRoll);
        }
        var ground=view.ground(mx,my);if(ground==null)return null;
        int x=Math.round(ground.x/.65f),z=Math.round(ground.z/.65f);
        var hit=view.pick(pieces,mx,my,true);
        if(hit!=null){
            var anchor=pieces.get(hit.index());
            if(Math.abs(hit.normal().x)>.5){x=anchor.x()+(int)hit.normal().x;z=anchor.z();}
            else if(Math.abs(hit.normal().z)>.5){x=anchor.x();z=anchor.z()+(int)hit.normal().z;}
        }
        if(Math.abs(x)>4||Math.abs(z)>4||x==0&&z==0)return null;
        return new Blueprint.Piece(x,z,palette,0);
    }
    private void add(Blueprint.Piece p){
        if(!p.mounted())pieces.removeIf(q->!q.mounted()&&q.x()==p.x()&&q.z()==p.z());
        pieces.add(p);selected=pieces.size()-1;
        if(symmetry&&Math.abs(p.px())>.05)pieces.add(p.mirror());
    }
    private void replaceSelected(Blueprint.Piece next){
        if(selected<0||selected>=pieces.size())return;var old=pieces.get(selected);
        if(symmetry&&Math.abs(old.px())>.05)for(int i=0;i<pieces.size();i++){
            var q=pieces.get(i);
            if(i!=selected&&q.part()==old.part()&&Math.abs(q.px()+old.px())<.001&&Math.abs(q.pz()-old.pz())<.001)pieces.set(i,next.mirror());
        }
        pieces.set(selected,next);
    }
    private void resize(double amount){
        scale=Math.clamp(scale+amount,.5,2);
        if(selected>=0){var p=pieces.get(selected);if(p.part()==Part.CORE||p.part()==Part.BODY||p.part()==Part.ARMOR)return;
            remember();replaceSelected(p.transform(p.angle(),Math.clamp(p.size()+amount,.5,2)));scale=pieces.get(selected).size();}
    }
    private void rotate(double amount){
        if(selected<0){if(rotationAxis==0)rotation=(rotation+amount)%360;else if(rotationAxis==1)presetPitch=(presetPitch+amount)%360;else presetRoll=(presetRoll+amount)%360;}
        if(selected>=0){var p=pieces.get(selected);if(p.part()==Part.CORE||p.part()==Part.BODY||p.part()==Part.ARMOR)return;
            remember();
            var next=rotationAxis==0?p.orient((p.angle()+amount)%360,p.pitch(),p.roll()):rotationAxis==1?p.orient(p.angle(),(p.pitch()+amount)%360,p.roll()):p.orient(p.angle(),p.pitch(),(p.roll()+amount)%360);
            replaceSelected(next);rotation=pieces.get(selected).angle();}
    }
    private void remove(){
        if(selected<0||pieces.get(selected).part()==Part.CORE)return;
        remember();var p=pieces.remove(selected);
        if(!p.mounted())pieces.removeIf(q->q.mounted()&&q.x()==p.x()&&q.z()==p.z());
        if(symmetry&&Math.abs(p.px())>.05)pieces.removeIf(q->q.part()==p.part()&&Math.abs(q.px()+p.px())<.001&&Math.abs(q.pz()-p.pz())<.001);
        selected=-1;
    }
    private void history(boolean forwards){
        var source=forwards?redo:undo;var destination=forwards?undo:redo;
        if(source.isEmpty())return;destination.push(List.copyOf(pieces));pieces.clear();pieces.addAll(source.pop());selected=-1;pending=null;
    }
    private void apply(){
        try{Blueprint next=draft();next.validate();if(!apply.active)return;
            JsonObject obj=new JsonObject();obj.addProperty("type","apply");obj.addProperty("revision",revision);
            obj.add("body",SporeCell.GSON.toJsonTree(next));CellClient.send(obj);
        }catch(RuntimeException ex){error=ex.getMessage();}
    }
    @Override public void render(DrawContext draw,int mx,int my,float delta){
        draw.fill(0,0,width,height,0xFF102A35);
        draw.drawCenteredTextWithShadow(textRenderer,"РЕДАКТОР ОРГАНИЗМА · ДНК "+CellClient.dna,width/2,10,0xD4F5E5);
        draw.drawCenteredTextWithShadow(textRenderer,"ЛКМ: деталь · ПКМ: обзор · Колесо: размер · Shift+колесо: камера",width/2,27,0xA7BFCA);
        draw.fill(left,top,right,bottom,0xFF173B48);
        draw.enableScissor(left,top,right,bottom);
        if(placing&&!functional())grid(draw);
        List<Blueprint.Piece> shown=new ArrayList<>(pieces);if(pending!=null)shown.add(pending);
        var matrices=draw.getMatrices();matrices.push();matrices.multiplyPositionMatrix(view.matrix());
        CellRenderer.renderDesign(new Blueprint(shown),matrices,draw.getVertexConsumers());draw.draw();matrices.pop();
        if(selected>=0&&selected<pieces.size()){
            var p=pieces.get(selected);var pt=view.project(p.px(),p.py()+.4*p.size(),p.pz());
            int r=(int)Math.max(7,view.zoom*.45*p.size()),x=(int)pt.x-r,y=(int)pt.y-r;
            draw.fill(x,y,x+2*r,y+1,0xFFFFDA75);draw.fill(x,y+2*r,x+2*r,y+2*r+1,0xFFFFDA75);
            draw.fill(x,y,x+1,y+2*r,0xFFFFDA75);draw.fill(x+2*r,y,x+2*r+1,y+2*r,0xFFFFDA75);
        }
        draw.disableScissor();
        int sx=width-135;Part part=selected>=0?pieces.get(selected).part():palette;
        draw.drawText(textRenderer,NAMES.get(part),sx,46,0xFFFFFF,false);
        draw.drawText(textRenderer,String.format(Locale.ROOT,"Размер: %.2f×",selected>=0?pieces.get(selected).size():scale),sx,64,0xD3E7E9,false);
        draw.drawText(textRenderer,String.format(Locale.ROOT,"Угол: %.0f°",selected>=0?(rotationAxis==0?pieces.get(selected).angle():rotationAxis==1?pieces.get(selected).pitch():pieces.get(selected).roll()):(rotationAxis==0?rotation:rotationAxis==1?presetPitch:presetRoll)),sx,80,0xD3E7E9,false);
        String status=error;
        try{
            var b=draft();var stats=b.stats(CellClient.specs);int cost=Blueprint.purchaseCost(original,b,CellClient.specs);
            draw.drawText(textRenderer,String.format(Locale.ROOT,"Скорость %.2f",stats.speed()),left+4,top+4,0xAECFD4,false);
            draw.drawText(textRenderer,String.format(Locale.ROOT,"Обзор %.0f",stats.sight()),left+4,top+16,0xAECFD4,false);
            apply.active=cost<=CellClient.dna&&(CellClient.progress>=100||pieces.stream().noneMatch(p->p.part()==Part.OMNIVORE_MOUTH));
            draw.drawText(textRenderer,"Расход: "+cost+" ДНК",sx,173,cost>CellClient.dna?0xFF8888:0xA3E8B2,false);
            draw.drawText(textRenderer,"Остаток: "+(CellClient.dna-cost),sx,184,0xFFFFFF,false);
            if(height>300){
                draw.drawText(textRenderer,String.format(Locale.ROOT,"Скорость: %.2f",stats.speed()),sx,213,0xD3E7E9,false);
                draw.drawText(textRenderer,String.format(Locale.ROOT,"Обзор: %.0f",stats.sight()),sx,226,0xD3E7E9,false);
                draw.drawText(textRenderer,"Пища: "+(stats.herbivore()?"растения ":"")+(stats.carnivore()?"мясо":""),sx,239,0xD3E7E9,false);
            }
        }catch(RuntimeException ex){apply.active=false;if(status.isEmpty())status=ex.getMessage();}
        symmetryButton.setMessage(Text.literal(symmetry?"Симметрия: вкл [S]":"Симметрия: выкл [S]"));
        draw.drawCenteredTextWithShadow(textRenderer,status.isEmpty()?"R: поворот · Z/Y: отмена/повтор · Del: убрать":status,width/2,height-43,status.isEmpty()?0xA7BFCA:0xFFD594);
        super.render(draw,mx,my,delta);
        int row=0;
        for(Part p:Part.values())if(p!=Part.CORE){
            int y=43+row++*paletteStep;
            if(p==palette&&placing)draw.fill(4,y,7,y+Math.min(20,paletteStep-1),0xFFFFFF66);
            var icon=draw.getMatrices();icon.push();icon.translate(19,y+13,200);icon.scale(13,-13,13);
            icon.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(35));
            icon.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(25));
            CellRenderer.renderDesign(new Blueprint(List.of(p==Part.BODY||p==Part.ARMOR?new Blueprint.Piece(0,0,p,0):Blueprint.Piece.attachment(0,0,p,0,0,0,0,1))),icon,draw.getVertexConsumers());
            draw.draw();icon.pop();
            if(mx>=7&&mx<123&&my>=y&&my<y+paletteStep)draw.drawOrderedTooltip(textRenderer,textRenderer.wrapLines(Text.literal(NAMES.get(p)+": перетащите на клетку. "+switch(p){case FLAGELLUM->"Увеличивает тягу.";case CILIA->"Улучшает поворот.";case EYE->"Увеличивает обзор.";case HERBIVORE_MOUTH->"Растительная пища.";case CARNIVORE_MOUTH->"Мясо и укусы.";case OMNIVORE_MOUTH->"Растения и мясо; доступен при росте 100.";case SPIKE->"Направленный контактный урон.";default->"Кубический сегмент тела.";}),180),mx,my);
        }
    }
    private void grid(DrawContext draw){
        for(int i=-4;i<=5;i++){
            var a=view.project((i-.5)*.65,-.03,-2.925);var b=view.project((i-.5)*.65,-.03,2.925);line(draw,a,b);
            a=view.project(-2.925,-.03,(i-.5)*.65);b=view.project(2.925,-.03,(i-.5)*.65);line(draw,a,b);
        }
    }
    private void line(DrawContext draw,Vector3f a,Vector3f b){
        int count=(int)Math.max(Math.abs(a.x-b.x),Math.abs(a.y-b.y));
        for(int i=0;i<=count;i++){float t=count==0?0:(float)i/count;int x=(int)(a.x+(b.x-a.x)*t),y=(int)(a.y+(b.y-a.y)*t);if(inside(x,y))draw.fill(x,y,x+1,y+1,0xFF285363);}
    }
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(!inside(mx,my))return super.mouseClicked(mx,my,button);
        if(button==1)return true;if(button!=0)return false;
        if(placing){pending=candidate(mx,my);return true;}
        var hit=view.pick(pieces,mx,my,false);
        if(hit!=null){selected=hit.index();var p=pieces.get(selected);scale=p.size();rotation=p.angle();presetPitch=p.pitch();presetRoll=p.roll();palette=p.part();gesture=List.copyOf(pieces);}else selected=-1;
        return true;
    }
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){
        if(button==1){view.yaw+=dx*.7f;view.pitch=Math.clamp(view.pitch+(float)dy*.5f,20,80);return true;}
        if(button==0&&inside(mx,my)){
            if(placing)pending=candidate(mx,my);
            else if(selected>=0&&pieces.get(selected).mounted()){var next=candidate(mx,my);if(next!=null){var old=pieces.get(selected);replaceSelected(next.orient(old.angle(),old.pitch(),old.roll()));}}
            return true;
        }
        return super.mouseDragged(mx,my,button,dx,dy);
    }
    @Override public boolean mouseReleased(double mx,double my,int button){
        if(button==0){
            if(placing&&inside(mx,my)){var next=candidate(mx,my);if(next!=null){remember();add(next);placing=false;}}
            else if(gesture!=null&&!gesture.equals(pieces)){undo.push(gesture);redo.clear();}
            pending=null;gesture=null;
        }
        return super.mouseReleased(mx,my,button);
    }
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){
        if(hasShiftDown())view.zoom=Math.clamp(view.zoom+(float)vertical*4,10,100);else resize(vertical*.1);return true;
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==GLFW.GLFW_KEY_R){rotate(hasShiftDown()?-15:15);return true;}
        if(key==GLFW.GLFW_KEY_S){symmetry=!symmetry;return true;}
        if(key==GLFW.GLFW_KEY_Z){history(false);return true;}
        if(key==GLFW.GLFW_KEY_Y){history(true);return true;}
        if(key==GLFW.GLFW_KEY_DELETE){remove();return true;}
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public void close(){CellClient.send("close");super.close();}
    @Override public boolean shouldPause(){return false;}
}
