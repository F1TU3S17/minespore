package dev.sporecell;

import com.google.gson.Gson;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.entity.*;
import net.minecraft.registry.*;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.slf4j.*;

public final class SporeCell implements ModInitializer {
    public static final Gson GSON=new Gson();
    public static final Logger LOGGER=LoggerFactory.getLogger("sporecell");
    public static final RegistryKey<World> POND=RegistryKey.of(RegistryKeys.WORLD,Identifier.of("sporecell","pond"));
    public static final EntityType<CellEntity> CELL=Registry.register(Registries.ENTITY_TYPE,Identifier.of("sporecell","cell"),
            EntityType.Builder.<CellEntity>create(CellEntity::new,SpawnGroup.MISC).dimensions(.6f,.5f)
                    .maxTrackingRange(128).trackingTickInterval(1).build("sporecell:cell"));
    @Override public void onInitialize() {
        Parts.load();
        CellAbilities.load();
        PayloadTypeRegistry.playC2S().register(CellPackets.Action.ID,CellPackets.Action.CODEC);
        PayloadTypeRegistry.playS2C().register(CellPackets.State.ID,CellPackets.State.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(CellPackets.Action.ID,(payload,context)-> {
            if(payload.json().length()>48000) return;
            context.server().execute(()->CellGame.action(context.player(),payload.json()));
        });
        CommandRegistrationCallback.EVENT.register((dispatcher,registry,environment)->dispatcher.register(
                CommandManager.literal("cell")
                        .then(CommandManager.literal("enter").executes(ctx->{CellGame.enter(ctx.getSource().getPlayerOrThrow());return 1;}))
                        .then(CommandManager.literal("leave").executes(ctx->{CellGame.leave(ctx.getSource().getPlayerOrThrow());return 1;}))
                        .then(CommandManager.literal("editor").executes(ctx->{CellGame.openEditor(ctx.getSource().getPlayerOrThrow());return 1;}))
                        .executes(ctx->{ctx.getSource().sendFeedback(()->Text.literal("/cell enter | /cell editor | /cell leave"),false);return 1;})));
        ServerTickEvents.END_SERVER_TICK.register(CellGame::serverTick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->CellGame.disconnect(handler.player));
    }
}
