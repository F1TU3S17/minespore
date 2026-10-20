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
    public static int dna,revision,eaten,deaths;
    public static float health;
    public static String message="";
    private static KeyBinding editor;
    private static int ticks,entityId=-1;
    @Override public void onInitializeClient() {
        EntityRendererRegistry.register(SporeCell.CELL,CellRenderer::new);
        editor=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.sporecell.editor",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_G,"category.sporecell"));
        ClientPlayNetworking.registerGlobalReceiver(CellPackets.State.ID,(packet,context)->context.client().execute(()-> {
            JsonObject s=JsonParser.parseString(packet.json()).getAsJsonObject();
            active=s.get("active").getAsBoolean();editing=s.get("editing").getAsBoolean();
            entityId=s.get("entityId").getAsInt();
            if(!active && context.client().player!=null)context.client().setCameraEntity(context.client().player);
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
            draw.fill(8,8,290,68,0xCC102E38);
            draw.drawText(client.textRenderer,"CELL STAGE  |  DNA "+dna+"  |  G: editor",16,15,0xD9F5E8,false);
            double max=body.stats(specs).health();
            draw.fill(16,33,216,40,0xFF34464E);draw.fill(16,33,16+(int)(200*Math.max(0,health)/max),40,0xFF71D9B5);
            draw.drawText(client.textRenderer,String.format(Locale.ROOT,"HP %.0f/%.0f   Food %d   Deaths %d",health,max,eaten,deaths),16,47,0xFFFFFF,false);
            if(!message.isEmpty())draw.drawText(client.textRenderer,message,16,76,0xFFE7B7,false);
        });
    }
    public static void send(String type) { JsonObject o=new JsonObject();o.addProperty("type",type);send(o); }
    public static void send(JsonObject obj) { if(ClientPlayNetworking.canSend(CellPackets.Action.ID))ClientPlayNetworking.send(new CellPackets.Action(obj.toString())); }
}
