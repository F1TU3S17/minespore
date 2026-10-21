package dev.sporecell.client;

import dev.sporecell.core.Evolution;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Evolution overview and the reproduction-to-editor transition. */
public final class CellEvolutionScreen extends Screen {
    private ButtonWidget evolve;
    final boolean offspring=CellClient.mating==2;
    public CellEvolutionScreen(){super(Text.literal("Эволюция организма"));}
    @Override protected void init(){
        evolve=addDrawableChild(ButtonWidget.builder(Text.literal(CellClient.mating==2?"Изменить потомка":"Брачный зов [G]"),b->{
            CellClient.send(CellClient.mating==2?"editor":"mate");
            if(CellClient.mating!=2)client.setScreen(null);
        }).dimensions(width/2-110,height-58,220,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Вернуться"),b->close()).dimensions(width/2-110,height-33,220,20).build());
    }
    @Override public void renderBackground(DrawContext draw,int mx,int my,float delta){}
    @Override public void render(DrawContext draw,int mx,int my,float delta){
        draw.fill(0,0,width,height,0xFF102A35);
        draw.drawCenteredTextWithShadow(textRenderer,"ЭВОЛЮЦИЯ ОРГАНИЗМА",width/2,15,0xB6EAD3);
        draw.drawCenteredTextWithShadow(textRenderer,"Поколение "+CellClient.generation+" · ДНК "+CellClient.dna+" · Рост "+CellClient.progress+"/1000",width/2,36,0xFFFFFF);
        int total=CellClient.plantFood+CellClient.animalFood;
        String diet=total==0?"Питание ещё не определено":CellClient.plantFood==0?"Хищная ветвь":CellClient.animalFood==0?"Травоядная ветвь":"Смешанное питание";
        draw.drawCenteredTextWithShadow(textRenderer,diet,width/2,54,0xF1D68A);
        draw.drawCenteredTextWithShadow(textRenderer,"Открытые части: "+dev.sporecell.core.CellUnlocks.count(CellClient.unlocked)+" / 6",width/2,66,0xBDD5DC);
        int start=width/2-150,y=80;
        draw.fill(start,y,start+300,y+8,0xFF314E5A);
        draw.fill(start,y,start+300*CellClient.progress/1000,y+8,0xFF79D6B2);
        for(int i=1;i<=10;i++){
            int x=start+(i-1)*30;
            draw.fill(x,y-3,x+1,y+12,0xFFBCD5CF);
            draw.drawText(textRenderer,Integer.toString(i),x,y+17,i<=CellClient.level?0xD5F7E0:0x6B8A92,false);
        }
        String status=switch(CellClient.mating){
            case 0->"Партнёр откликнулся: приблизьтесь к нему.";
            case 1->"Размножение: формируется яйцо потомка.";
            case 2->"Размножение завершено. Доступен редактор потомка.";
            default->CellClient.breedNeeded==0?"Клетка готова к размножению. Подайте брачный зов.":"До размножения нужно пищи: "+CellClient.breedNeeded;
        };
        draw.drawCenteredTextWithShadow(textRenderer,status,width/2,y+42,0xF1D68A);
        int historyY=y+63;
        for(int i=Math.max(0,CellClient.history.size()-Math.max(0,(height-historyY-70)/14));i<CellClient.history.size();i++){
            var event=CellClient.history.get(i);
            draw.drawCenteredTextWithShadow(textRenderer,"Поколение "+event.generation()+" · Рост "+event.progress()+" · Растения "+event.plantFood()+" / Мясо "+event.animalFood(),width/2,historyY,0xBDD5DC);historyY+=14;
        }
        evolve.active=CellClient.mating==2||CellClient.mating<0&&CellClient.breedNeeded==0;
        evolve.setMessage(Text.literal(CellClient.mating==2?"Изменить потомка":"Брачный зов [G]"));
        super.render(draw,mx,my,delta);
    }
    @Override public void close(){CellClient.send("close");super.close();}
    @Override public boolean shouldPause(){return false;}
}
