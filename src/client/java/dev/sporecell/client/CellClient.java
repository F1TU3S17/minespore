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
    public static int dna,revision,eaten,deaths,progress,level=1;
    static Evolution.Diet pendingDiet;
    public static float health;
    public static String message="";
    private static KeyBinding editor;
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
        ClientPlayNetworking.registerGlobalReceiver(CellPackets.State.ID,(packet,context)->context.client().execute(()-> {
            JsonObject s=JsonParser.parseString(packet.json()).getAsJsonObject();
            active=s.get("active").getAsBoolean();editing=s.get("editing").getAsBoolean();
            entityId=s.get("entityId").getAsInt();
            if(!active && context.client().player!=null)context.client().setCameraEntity(context.client().player);
            progress=s.get("progress").getAsInt();level=s.get("level").getAsInt();
            dna=s.get("dna").getAsInt();health=s.get("health").getAsFloat();revision=s.get("revision").getAsInt();
            eaten=s.get("eaten").getAsInt();deaths=s.get("deaths").getAsInt();message=s.get("message").getAsString();
            body=SporeCell.GSON.fromJson(s.get("body"),Blueprint.class);
            EnumMap<Part,PartSpec> map=new EnumMap<>(Part.class);
            for(Part p:Part.values())map.put(p,SporeCell.GSON.fromJson(s.getAsJsonObject("specs").get(p.name()),PartSpec.class));
            specs=map;
            if(s.get("editor").getAsBoolean() && !(context.client().currentScreen instanceof CellEditor))context.client().setScreen(new CellEditor());
            else if(!editing && context.client().currentScreen instanceof CellEditor)context.client().setScreen(null);
        }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)-> { active=false;editing=false;message=""; });
        ClientTickEvents.END_CLIENT_TICK.register(client-> {
            if(pendingDiet!=null && client.player!=null && ClientPlayNetworking.canSend(CellPackets.Action.ID)) {
                JsonObject start=new JsonObject();start.addProperty("type","start");start.addProperty("diet",pendingDiet.name());send(start);pendingDiet=null;
            }
            if(!active || client.player==null)return;
            if(client.world!=null && client.world.getEntityById(entityId) instanceof CellEntity cell && client.getCameraEntity()!=cell)
                client.setCameraEntity(cell);
            while(editor.wasPressed()) if(client.currentScreen==null)send("editor");
            if(++ticks%2!=0)return;
            double x=0,z=0;
            if(client.currentScreen==null) {
                x=(client.options.rightKey.isPressed()?1:0)-(client.options.leftKey.isPressed()?1:0);
                z=(client.options.backKey.isPressed()?1:0)-(client.options.forwardKey.isPressed()?1:0);
            }
            JsonObject action=new JsonObject();action.addProperty("type","move");action.addProperty("x",x);action.addProperty("z",z);send(action);
        });
        HudRenderCallback.EVENT.register((draw,tickCounter)-> {
            if(!active||specs.isEmpty())return;
            var client=net.minecraft.client.MinecraftClient.getInstance();
            int y=draw.getScaledWindowHeight()-48;
            draw.fill(8,y,260,y+40,0xC9102E38);
            draw.drawText(client.textRenderer,"ДНК "+dna+" · Уровень "+level+" · G: редактор",16,y+5,0xD9F5E8,false);
            double max=body.stats(specs).health();
            draw.fill(16,y+20,106,y+25,0xFF34464E);
            draw.fill(16,y+20,16+(int)(90*Math.max(0,health)/max),y+25,0xFF71D9B5);
            draw.drawText(client.textRenderer,String.format(Locale.ROOT,"Жизнь %.0f/%.0f",health,max),16,y+29,0xD9F5E8,false);
            draw.fill(140,y+20,250,y+25,0xFF34464E);
            draw.fill(140,y+20,140+(int)(110*progress/1000d),y+25,0xFFEAD27A);
            draw.drawText(client.textRenderer,"Рост "+progress+"/1000",140,y+29,0xD9F5E8,false);
            if(!message.isEmpty()&&client.currentScreen==null)draw.drawCenteredTextWithShadow(client.textRenderer,message,draw.getScaledWindowWidth()/2,12,0xFFE7B7);
        });
    }
    public static void send(String type) { JsonObject o=new JsonObject();o.addProperty("type",type);send(o); }
    public static void send(JsonObject obj) { if(ClientPlayNetworking.canSend(CellPackets.Action.ID))ClientPlayNetworking.send(new CellPackets.Action(obj.toString())); }
}
