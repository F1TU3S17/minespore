package dev.sporecell;

import com.google.gson.JsonObject;
import dev.sporecell.core.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import java.util.*;

/** Gameplay verification through the same public actions as a networked player. */
public final class CellIntegrationTest implements FabricGameTest {
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=300)
    public void cellLoop(TestContext context){
        var profile=new com.mojang.authlib.GameProfile(UUID.randomUUID(),"cell-test-player");
        var data=net.minecraft.server.network.ConnectedClientData.createDefault(profile,false);
        var player=new net.minecraft.server.network.ServerPlayerEntity(context.getWorld().getServer(),context.getWorld(),profile,data.syncedOptions());
        var connection=new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        player.getServer().getPlayerManager().onPlayerConnect(connection,player,data);player.changeGameMode(GameMode.CREATIVE);
        var originalWorld=player.getWorld().getRegistryKey();CellGame.enter(player);
        var world=player.getServer().getWorld(SporeCell.POND);
        context.assertTrue(player.getCameraEntity() instanceof CellEntity,"Separate cell entity and camera");
        CellEntity cell=(CellEntity)player.getCameraEntity();
        CellSave save=CellSave.get(player.getServer());var p=save.profile(player.getUuid());
        for(int x=-1;x<=4;x++)for(int z=-1;z<=1;z++)world.setChunkForced(x,z,true);
        context.runAtTick(20,()->{
            clear(world,cell);cell.setPosition(55,66.05,0);cell.setYaw(90);cell.setVelocity(Vec3d.ZERO);
            CellGame.openEditor(player);
            context.assertTrue(!CellReproduction.ready(player.getUuid()),"Editor cannot bypass reproduction");
            CellGame.action(player,"{\"type\":\"mate\"}");
            context.assertTrue(CellReproduction.phase(player.getUuid())<0,"Cannot mate before earning food");
        });
        for(int tick=21;tick<50;tick++)context.runAtTick(tick,()->CellGame.action(player,"{\"type\":\"move\",\"x\":1,\"z\":0}"));
        context.runAtTick(55,()->{
            context.assertTrue(cell.getX()>55.3,"Movement beyond the old arena");
            clear(world,cell);cell.inputX=cell.inputZ=0;cell.setVelocity(Vec3d.ZERO);cell.setPosition(0,66.05,0);cell.setYaw(0);
            for(int i=0;i<5;i++){var food=new CellEntity(SporeCell.CELL,world);food.kind(CellEntity.PLANT);food.setPosition(0,66.05,-1.35);world.spawnEntity(food);}
        });
        context.runAtTick(75,()->{
            context.assertTrue(p.progress==50&&p.eaten==5&&p.dna==5,"Food earns progress, DNA and reproduction eligibility");
            CellGame.action(player,"{\"type\":\"mate\"}");
            context.assertTrue(CellReproduction.phase(player.getUuid())==0,"Call creates an approaching partner");
            var mate=(CellEntity)world.getEntityById(CellReproduction.partnerId(player.getUuid()));
            context.assertTrue(mate!=null&&mate.kind()==CellEntity.MATE,"Mate is a real entity");
            mate.setPosition(cell.getPos().add(.5,0,0));
        });
        context.runAtTick(125,()->{
            context.assertTrue(CellReproduction.ready(player.getUuid()),"Contact and breeding complete before editor is granted");
            CellGame.openEditor(player);
            var pieces=new ArrayList<>(p.blueprint.pieces());
            pieces.add(Blueprint.Piece.attachment(0,0,Part.EYE,.2,0,0,30,1.5));
            Blueprint next=new Blueprint(pieces);int budget=p.dna;
            JsonObject apply=new JsonObject();apply.addProperty("type","apply");apply.addProperty("revision",p.revision);apply.add("body",SporeCell.GSON.toJsonTree(next));
            CellGame.action(player,apply.toString());
            context.assertTrue(p.blueprint.equals(next)&&cell.blueprint().equals(next),"Server applies attached part with full transforms");
            context.assertTrue(p.dna==budget-3&&p.generation==1&&p.history.size()==1,"Exactly one generation and DNA purchase");
            context.assertTrue(!CellReproduction.ready(player.getUuid()),"Editing permission is consumed by reproduction");
            var restored=CellSave.TYPE.deserializer().apply(save.writeNbt(new net.minecraft.nbt.NbtCompound(),world.getRegistryManager()),world.getRegistryManager()).profile(player.getUuid());
            context.assertTrue(restored.blueprint.equals(next)&&restored.generation==1&&restored.history.equals(p.history),"NBT preserves attachments and evolution history");
            int revision=p.revision;
            CellGame.action(player,apply.toString());
            context.assertTrue(p.revision==revision&&p.dna==budget-3,"Apply replay cannot evolve without another mating");
            CellGame.openEditor(player);
            context.assertTrue(!CellReproduction.ready(player.getUuid()),"Editor cannot be reopened without breeding");
            for(int i=0;i<5;i++){var food=new CellEntity(SporeCell.CELL,world);food.kind(CellEntity.PLANT);food.setPosition(0,66.05,-1.35);world.spawnEntity(food);}
        });
        context.runAtTick(150,()->{
            context.assertTrue(p.progress==100&&cell.level()==2&&cell.size()>1,"New food grows the offspring");
            clear(world,cell);
            var attacker=new CellEntity(SporeCell.CELL,world);attacker.kind(CellEntity.HUNTER);
            attacker.blueprint(new Blueprint(List.of(new Blueprint.Piece(0,0,Part.CORE,0),new Blueprint.Piece(0,-1,Part.CARNIVORE_MOUTH,0))));
            cell.setPosition(0,66.05,0);cell.setVelocity(Vec3d.ZERO);cell.hurtCooldown=0;
            attacker.setPosition(0,66.05,-2.1);attacker.setYaw(180);attacker.age=4;float hp=cell.health();attacker.tick();
            context.assertTrue(cell.health()<hp,"Visible health state changes on a directed bite");
            int deaths=p.deaths;cell.health(1);cell.hurtCooldown=0;attacker.age=4;attacker.tick();
            context.assertTrue(p.deaths==deaths+1&&cell.health()==6,"Lethal damage respawns the offspring");
            context.assertTrue(p.generation==1&&p.blueprint.pieces().getLast().size()==1.5,"Death retains generation and transformed parts");
            CellGame.leave(player);
            context.assertTrue(player.getWorld().getRegistryKey().equals(originalWorld)&&player.interactionManager.getGameMode()==GameMode.CREATIVE,"Normal Minecraft is restored");
            for(int x=-1;x<=4;x++)for(int z=-1;z<=1;z++)world.setChunkForced(x,z,false);
            player.getServer().getPlayerManager().remove(player);context.complete();
        });
    }
    private static void clear(net.minecraft.server.world.ServerWorld world,CellEntity player){
        for(var e:world.getEntitiesByClass(CellEntity.class,new Box(-80,0,-80,90,100,80),e->e!=player&&e.kind()<CellEntity.MATE))e.discard();
    }
}
