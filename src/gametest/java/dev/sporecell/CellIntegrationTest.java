package dev.sporecell;

import com.google.gson.JsonObject;
import dev.sporecell.core.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import java.util.*;

/** Runs on the real Minecraft dedicated GameTest server, never shipped in the mod jar. */
public final class CellIntegrationTest implements FabricGameTest {
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=200)
    public void cellLoop(TestContext context) {
        var profile=new com.mojang.authlib.GameProfile(UUID.randomUUID(),"cell-test-player");
        var connectionData=net.minecraft.server.network.ConnectedClientData.createDefault(profile,false);
        var player=new net.minecraft.server.network.ServerPlayerEntity(context.getWorld().getServer(),context.getWorld(),profile,connectionData.syncedOptions());
        var connection=new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        player.getServer().getPlayerManager().onPlayerConnect(connection,player,connectionData);
        player.changeGameMode(GameMode.CREATIVE);
        var originalWorld=player.getWorld().getRegistryKey();
        CellGame.enter(player);
        var world=player.getServer().getWorld(SporeCell.POND);
        context.assertTrue(world!=null,"Pond dimension loads from datapack");
        context.assertTrue(player.getCameraEntity() instanceof CellEntity,"Server camera is attached to a cell");
        CellEntity cell=(CellEntity)player.getCameraEntity();
        context.assertTrue(player.isSpectator(),"Separate mode uses spectator camera host");
        context.assertTrue(player.getCameraEntity()==cell,"Camera follows the cell entity");
        // Let Minecraft activate freshly loaded chunks and index entities before interactions.
        context.runAtTick(20,()-> {
            for(CellEntity e:world.getEntitiesByClass(CellEntity.class,new Box(-50,60,-50,50,80,50),e->e!=cell))e.discard();
            cell.setPosition(0,66.05,0);cell.setYaw(90);cell.setVelocity(Vec3d.ZERO);
        });
        for(int tick=21;tick<50;tick++)context.runAtTick(tick,()->CellGame.action(player,"{\"type\":\"move\",\"x\":1,\"z\":0}"));
        context.runAtTick(55,()-> {
        for(CellEntity e:world.getEntitiesByClass(CellEntity.class,new Box(-50,60,-50,50,80,50),e->e!=cell))e.discard();
        context.assertTrue(cell.getX()>.3,"Server input moves the cell");
        context.assertTrue(Math.abs(cell.getY()-66.05)<.001,"Motion stays on the gameplay plane");
        CellGame.action(player,"{\"type\":\"move\",\"x\":0,\"z\":0}");cell.setPosition(0,66.05,0);cell.setYaw(0);cell.setVelocity(Vec3d.ZERO);
        CellSave save=CellSave.get(player.getServer());CellSave.Profile p=save.profile(player.getUuid());int dna=p.dna;
        CellEntity food=new CellEntity(SporeCell.CELL,world);food.kind(CellEntity.PLANT);food.setPosition(0,66.05,-1.35);world.spawnEntity(food);
        context.runAtTick(75,()-> {
        context.assertTrue(food.isRemoved(),"Forward plant mouth consumes reachable plant food");
        context.assertTrue(p.dna==dna+2,"Eating earns exactly 2 DNA: before="+dna+", after="+p.dna+", eaten="+p.eaten+", cell="+cell.getPos()+", removed="+cell.isRemoved());
        CellGame.openEditor(player);
        List<Blueprint.Piece> pieces=new ArrayList<>(p.blueprint.pieces());pieces.add(new Blueprint.Piece(1,0,Part.ARMOR,1));
        Blueprint next=new Blueprint(pieces);int before=p.dna;
        JsonObject apply=new JsonObject();apply.addProperty("type","apply");apply.addProperty("revision",p.revision);apply.add("body",SporeCell.GSON.toJsonTree(next));
        CellGame.action(player,apply.toString());
        context.assertTrue(p.blueprint.equals(next)&&cell.blueprint().equals(next),"Editor applies the same blueprint to progress and entity");
        context.assertTrue(p.dna==before-Parts.SPECS.get(Part.ARMOR).cost(),"Server debits DNA once");
        context.assertTrue(p.blueprint.stats(Parts.SPECS).health()>Blueprint.starter().stats(Parts.SPECS).health(),"Armor increases health");
        var nbt=save.writeNbt(new net.minecraft.nbt.NbtCompound(),world.getRegistryManager());
        var restored=CellSave.TYPE.deserializer().apply(nbt,world.getRegistryManager()).profile(player.getUuid());
        context.assertTrue(restored.blueprint.equals(next)&&restored.dna==p.dna,"Saved profile preserves body and DNA");
        context.runAtTick(90,()-> {
            CellGame.openEditor(player);int budget=p.dna,rev=p.revision;
            Blueprint invalid=new Blueprint(List.of(new Blueprint.Piece(0,0,Part.CORE,0),new Blueprint.Piece(3,0,Part.SPIKE,0)));
            apply.addProperty("revision",rev);apply.add("body",SporeCell.GSON.toJsonTree(invalid));CellGame.action(player,apply.toString());
            context.assertTrue(p.revision==rev&&p.dna==budget&&p.blueprint.equals(next),"Disconnected construction cannot spend DNA or replace the cell");
            CellGame.action(player,"{\"type\":\"close\"}");
            CellEntity attacker=new CellEntity(SporeCell.CELL,world);
            attacker.kind(CellEntity.HUNTER);attacker.blueprint(new Blueprint(List.of(new Blueprint.Piece(0,0,Part.CORE,0),new Blueprint.Piece(0,-1,Part.CARNIVORE_MOUTH,0))));
            cell.setPosition(0,66.05,0);cell.setYaw(0);cell.setVelocity(Vec3d.ZERO);cell.hurtCooldown=0;
            attacker.setPosition(0,66.05,-1.35);attacker.setYaw(180);attacker.age=4;
            float hp=cell.health();attacker.tick();
            context.assertTrue(cell.health()<hp,"A facing carnivore mouth damages the target body");
            int deaths=p.deaths;cell.health(1);cell.hurtCooldown=0;attacker.age=4;attacker.tick();
            context.assertTrue(p.deaths==deaths+1 && cell.health()>1,"Lethal damage respawns the player cell");
            context.assertTrue(p.blueprint.equals(next)&&p.dna==budget,"Death retains construction and DNA");
            CellGame.leave(player);
            context.assertTrue(player.getWorld().getRegistryKey().equals(originalWorld),"Leaving restores the original dimension");
            context.assertTrue(player.interactionManager.getGameMode()==GameMode.CREATIVE,"Leaving restores the original game mode");
            context.assertTrue(!p.active,"Leaving disables the cell stage");
            player.getServer().getPlayerManager().remove(player);context.complete();
        });
        });
        });
    }
}
