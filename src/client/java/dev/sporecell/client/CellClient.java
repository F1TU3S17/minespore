package dev.sporecell.client;

import com.google.gson.*;
import dev.sporecell.*;
import dev.sporecell.core.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import java.util.*;

public final class CellClient implements ClientModInitializer {
    public static boolean active,editing;
    public static Blueprint body=Blueprint.starter();
    public static Map<Part,PartSpec> specs=new EnumMap<>(Part.class);
    public static int dna,revision,eaten,deaths,progress,level=1,generation,mating=-1,mateId=-1,breedNeeded=5,plantFood,animalFood;
    public static java.util.List<CellSave.EvolutionEvent> history=new ArrayList<>();
    static Evolution.Diet pendingDiet;
    public static float health,targetHealth,targetDamage;
    public static int targetId=-1,targetLevel,targetSerial,targetFlash=-100;
    public static String message="";
    public static Set<Part> unlocked=CellUnlocks.initial();
    private static Part foundPart;
    private static int foundSerial,foundAt=-1000;
    private static KeyBinding editor,evolution;
    private static int ticks,entityId=-1;
    @Override public void onInitializeClient() {
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client,screen,w,h)-> {
            if(screen instanceof net.minecraft.client.gui.screen.TitleScreen)
                net.fabricmc.fabric.api.client.screen.v1.Screens.getButtons(screen).add(net.minecraft.client.gui.widget.ButtonWidget.builder(
                    net.minecraft.text.Text.literal("Spore: стадия клетки"),b->client.setScreen(new CellModeScreen(screen))).dimensions(w/2-100,h/4+24,200,20).build());
        });
        PondRenderer.register();
        EntityRendererRegistry.register(SporeCell.CELL,CellRenderer::new);
        editor=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.sporecell.editor",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_G,"category.sporecell"));
        evolution=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.sporecell.evolution",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_V,"category.sporecell"));
        ClientPlayNetworking.registerGlobalReceiver(CellPackets.State.ID,(packet,context)->context.client().execute(()-> {
            JsonObject s=JsonParser.parseString(packet.json()).getAsJsonObject();
            active=s.get("active").getAsBoolean();editing=s.get("editing").getAsBoolean();
            unlocked=EnumSet.noneOf(Part.class);for(var part:s.getAsJsonArray("unlocked"))unlocked.add(Part.valueOf(part.getAsString()));
            if(s.has("discovery")&&!s.get("discovery").isJsonNull()){
                var found=s.getAsJsonObject("discovery");int serial=found.get("serial").getAsInt();
                if(foundSerial!=serial){foundSerial=serial;foundPart=Part.valueOf(found.get("part").getAsString());foundAt=ticks;
                    context.client().getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.ENTITY_PLAYER_LEVELUP,1.3f));}
            }
            entityId=s.get("entityId").getAsInt();
            if(!active && context.client().player!=null)context.client().setCameraEntity(context.client().player);
            generation=s.get("generation").getAsInt();mating=s.get("mating").getAsInt();mateId=s.get("mateId").getAsInt();breedNeeded=s.get("breedNeeded").getAsInt();
            plantFood=s.get("plantFood").getAsInt();animalFood=s.get("animalFood").getAsInt();
            history=new ArrayList<>();for(var event:s.getAsJsonArray("history"))history.add(SporeCell.GSON.fromJson(event,CellSave.EvolutionEvent.class));
            progress=s.get("progress").getAsInt();level=s.get("level").getAsInt();
            dna=s.get("dna").getAsInt();health=s.get("health").getAsFloat();revision=s.get("revision").getAsInt();
            eaten=s.get("eaten").getAsInt();deaths=s.get("deaths").getAsInt();message=s.get("message").getAsString();
            if(s.has("target")&&!s.get("target").isJsonNull()){
                var target=s.getAsJsonObject("target");targetId=target.get("id").getAsInt();targetLevel=target.get("level").getAsInt();targetHealth=target.get("health").getAsFloat();targetDamage=target.get("damage").getAsFloat();
                int serial=target.get("serial").getAsInt();if(serial!=targetSerial){targetSerial=serial;targetFlash=ticks;}
            }else targetId=-1;
            body=SporeCell.GSON.fromJson(s.get("body"),Blueprint.class);
            EnumMap<Part,PartSpec> map=new EnumMap<>(Part.class);
            for(Part p:Part.values())map.put(p,SporeCell.GSON.fromJson(s.getAsJsonObject("specs").get(p.name()),PartSpec.class));
            specs=map;
            if(s.get("editor").getAsBoolean() && !(context.client().currentScreen instanceof CellEditor))context.client().setScreen(new CellEditor());
            else if(!editing && context.client().currentScreen instanceof CellEditor)context.client().setScreen(null);
            if(mating<0&&context.client().currentScreen instanceof CellEvolutionScreen screen&&screen.offspring)context.client().setScreen(null);
            if(s.get("evolution").getAsBoolean() && !(context.client().currentScreen instanceof CellEditor) && !(context.client().currentScreen instanceof CellEvolutionScreen))
                context.client().setScreen(new CellEvolutionScreen());
            if(!active){MouseSteering.held=false;if(context.client().currentScreen==null)context.client().mouse.lockCursor();}
        }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)-> { active=false;editing=false;message="";MouseSteering.held=false;foundPart=null;foundSerial=0;unlocked=CellUnlocks.initial(); });
        ClientTickEvents.END_CLIENT_TICK.register(client-> {
            if(pendingDiet!=null && client.player!=null && ClientPlayNetworking.canSend(CellPackets.Action.ID)) {
                JsonObject start=new JsonObject();start.addProperty("type","start");start.addProperty("diet",pendingDiet.name());send(start);pendingDiet=null;
            }
            if(!active || client.player==null)return;
            if(client.world!=null && client.world.getEntityById(entityId) instanceof CellEntity cell && client.getCameraEntity()!=cell)
                client.setCameraEntity(cell);
            if(client.mouse.isCursorLocked())client.mouse.unlockCursor();
            if(client.currentScreen!=null)MouseSteering.held=false;
            while(editor.wasPressed())if(client.currentScreen==null)send("mate");
            while(evolution.wasPressed())if(client.currentScreen==null)client.setScreen(new CellEvolutionScreen());
            if(++ticks%2!=0)return;
            double x=0,z=0;
            if(client.currentScreen==null) {
                x=(client.options.rightKey.isPressed()?1:0)-(client.options.leftKey.isPressed()?1:0);
                z=(client.options.backKey.isPressed()?1:0)-(client.options.forwardKey.isPressed()?1:0);
                if(MouseSteering.held&&client.getCameraEntity() instanceof CellEntity cell){var direction=MouseSteering.direction(client,cell);x=direction.x;z=direction.z;}
            }
            JsonObject action=new JsonObject();action.addProperty("type","move");action.addProperty("x",x);action.addProperty("z",z);send(action);
        });
        HudRenderCallback.EVENT.register((draw,tickCounter)-> {
            if(!active||specs.isEmpty())return;
            var client=net.minecraft.client.MinecraftClient.getInstance();
            int y=draw.getScaledWindowHeight()-48;
            if(client.currentScreen==null){
                int known=CellUnlocks.count(unlocked);
                draw.fill(8,8,162,37,0xC9102E38);
                draw.drawText(client.textRenderer,"Части найдены: "+known+"/6",14,12,0xD9F5E8,false);
                draw.drawText(client.textRenderer,eaten<5?"Съешьте пищи: "+Math.min(5,eaten)+"/5":known<6?"Ищите золотые фрагменты":"Растите и меняйте потомков",14,25,0xEAD27A,false);
                if(foundPart!=null&&ticks-foundAt<120){
                    int fx=draw.getScaledWindowWidth()-174;
                    draw.fill(fx,45,draw.getScaledWindowWidth()-8,100,0xF02A4850);
                    draw.drawText(client.textRenderer,"Новая часть!",fx+8,51,0xFFE38B,false);
                    draw.drawText(client.textRenderer,CellEditor.NAMES.get(foundPart),fx+40,69,0xFFFFFF,false);
                    draw.drawText(client.textRenderer,"Доступна в редакторе",fx+40,83,0xBDE5D3,false);
                    var matrix=draw.getMatrices();matrix.push();matrix.translate(fx+22,83,200);matrix.scale(23,-23,23);
                    matrix.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(30));matrix.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(25));
                    CellRenderer.renderDesign(new Blueprint(List.of(Blueprint.Piece.attachment(0,0,foundPart,0,0,0,0,1))),matrix,draw.getVertexConsumers());draw.draw();matrix.pop();
                }
            }
            draw.fill(8,y,260,y+40,0xC9102E38);
            draw.drawText(client.textRenderer,"ДНК "+dna+" · Уровень "+level+" · G: зов · V: эволюция",16,y+5,0xD9F5E8,false);
            double max=body.stats(specs).health();
            draw.fill(16,y+20,106,y+25,0xFF34464E);
            draw.fill(16,y+20,16+(int)(90*Math.max(0,health)/max),y+25,0xFF71D9B5);
            draw.drawText(client.textRenderer,String.format(Locale.ROOT,"Жизнь %.0f/%.0f",health,max),16,y+29,0xD9F5E8,false);
            draw.fill(140,y+20,250,y+25,0xFF34464E);
            draw.fill(140,y+20,140+(int)(110*progress/1000d),y+25,0xFFEAD27A);
            draw.drawText(client.textRenderer,"Рост "+progress+"/1000",140,y+29,0xD9F5E8,false);
            if(targetId>=0&&client.currentScreen==null){
                int tx=draw.getScaledWindowWidth()-150;
                draw.fill(tx,8,tx+140,45,0xD0253642);
                draw.drawCenteredTextWithShadow(client.textRenderer,targetHealth<=0?"Противник побеждён":"Цель · уровень "+targetLevel,tx+70,12,0xFFD8BB);
                draw.fill(tx+8,26,tx+132,31,0xFF4A4147);draw.fill(tx+8,26,tx+8+(int)(124*Math.clamp(targetHealth/6,0,1)),31,0xFFEF8679);
                if(ticks-targetFlash<20)draw.drawText(client.textRenderer,String.format(Locale.ROOT,"−%.0f",targetDamage),tx+58,34,0xFFFFBA8E,false);
            }
            if(mating==0)draw.drawCenteredTextWithShadow(client.textRenderer,"Партнёр откликнулся на зов",draw.getScaledWindowWidth()/2,28,0xF0AFD5);
            if(mating==1)draw.drawCenteredTextWithShadow(client.textRenderer,"Размножение...",draw.getScaledWindowWidth()/2,28,0xF0AFD5);
            if(!message.isEmpty()&&client.currentScreen==null){
                var lines=client.textRenderer.wrapLines(net.minecraft.text.Text.literal(message),Math.min(360,draw.getScaledWindowWidth()-24));
                int messageY=draw.getScaledWindowHeight()-60-lines.size()*10;
                for(var line:lines){draw.drawTextWithShadow(client.textRenderer,line,(draw.getScaledWindowWidth()-client.textRenderer.getWidth(line))/2,messageY,0xFFE7B7);messageY+=10;}
            }
        });
    }
    public static void send(String type) { JsonObject o=new JsonObject();o.addProperty("type",type);send(o); }
    public static void send(JsonObject obj) { if(ClientPlayNetworking.canSend(CellPackets.Action.ID))ClientPlayNetworking.send(new CellPackets.Action(obj.toString())); }
}
