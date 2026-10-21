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
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100)
    public void cellAbilities(TestContext context){
        var world=context.getWorld().getServer().getWorld(SporeCell.POND);world.setChunkForced(12,0,true);world.setChunkForced(13,0,true);
        world.getChunk(12,0);world.getChunk(13,0);
        context.runAtTick(20,()->{
        var electric=organism(world,200,Part.ELECTRIC);var immune=organism(world,201,Part.ELECTRIC);var victim=organism(world,202,null);
        var mixedParts=new ArrayList<>(electric.blueprint().pieces());mixedParts.add(Blueprint.Piece.attachment(0,0,Part.POISON,.2,0,0,0,1));electric.blueprint(new Blueprint(mixedParts));
        electric.age=4;electric.tick();
        context.assertTrue(victim.health()==4&&victim.stunTicks>0,"Electric organ damages and stuns nearby cells");
        context.assertTrue(immune.health()==6,"Electric organ grants electric immunity");
        var mixedClouds=world.getEntitiesByClass(CellEntity.class,new Box(198,65,-4,204,70,4),e->e.kind()==CellEntity.POISON_CLOUD);
        context.assertTrue(!mixedClouds.isEmpty(),"Poison and electric organs both work on the same cell");mixedClouds.forEach(CellEntity::discard);
        var poison=organism(world,210,Part.POISON);var poisoned=organism(world,211,null);var poisonImmune=organism(world,210.5,Part.POISON);
        poison.age=4;poison.tick();
        var clouds=world.getEntitiesByClass(CellEntity.class,new Box(208,65,-4,214,70,4),e->e.kind()==CellEntity.POISON_CLOUD);
        context.assertTrue(!clouds.isEmpty(),"Poison organ creates an actual cloud");
        for(var cloud:clouds){cloud.age=10;cloud.tick();}
        context.assertTrue(poisoned.poisonTicks>0&&poisonImmune.poisonTicks==0,"Cloud applies poison with matching immunity");
        poisoned.poisonTicks=41;poisoned.tick();context.assertTrue(poisoned.health()==5,"Poison damages over time");
        for(var cloud:clouds){cloud.cloudTicks=1;cloud.tick();context.assertTrue(cloud.isRemoved(),"Poison cloud expires");}
        for(var entity:List.of(electric,immune,victim,poison,poisoned,poisonImmune))entity.discard();
        world.setChunkForced(12,0,false);world.setChunkForced(13,0,false);context.complete();
        });
    }
    private static CellEntity organism(net.minecraft.server.world.ServerWorld world,double x,Part part){
        var e=new CellEntity(SporeCell.CELL,world);e.kind(CellEntity.GRAZER);e.level(1);
        var pieces=new ArrayList<Blueprint.Piece>();pieces.add(new Blueprint.Piece(0,0,Part.CORE,0));
        if(part!=null)pieces.add(Blueprint.Piece.attachment(0,0,part,0,0,0,0,1));
        e.blueprint(new Blueprint(pieces));e.setPosition(x,66.05,0);world.spawnEntity(e);return e;
    }
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
            var fragment=CellDiscovery.drop(world,55.2,0,1,Part.SPIKE,true);int dna=p.dna;
            var fragmentNbt=fragment.writeNbt(new net.minecraft.nbt.NbtCompound());var loadedFragment=new CellEntity(SporeCell.CELL,world);loadedFragment.readNbt(fragmentNbt);
            context.assertTrue(loadedFragment.kind()==CellEntity.METEOR&&loadedFragment.loot()==Part.SPIKE,"World fragment metadata survives entity NBT");
            CellDiscovery.collect(player,cell);
            context.assertTrue(fragment.isRemoved()&&p.unlocked.contains(Part.SPIKE)&&p.dna==dna,"Meteor discovery unlocks an organ without granting DNA");
            int revision=p.revision;var duplicate=CellDiscovery.drop(world,55.2,0,1,Part.SPIKE,true);CellDiscovery.collect(player,cell);
            context.assertTrue(p.revision==revision,"Duplicate fragment cannot grant a second discovery");duplicate.discard();
            CellGame.openEditor(player);
            context.assertTrue(!CellReproduction.ready(player.getUuid()),"Editor cannot bypass reproduction");
            CellGame.action(player,"{\"type\":\"mate\"}");
            context.assertTrue(CellReproduction.phase(player.getUuid())<0,"Cannot mate before earning food");
        });
        for(int tick=21;tick<50;tick++)context.runAtTick(tick,()->CellGame.action(player,"{\"type\":\"move\",\"x\":1,\"z\":0}"));
        context.runAtTick(55,()->{
            context.assertTrue(cell.getX()>55.3,"Movement beyond the old arena");
            clear(world,cell);p.progress=p.eaten=p.dna=p.plantFood=p.animalFood=0;cell.level(1);cell.health(6);cell.hurtCooldown=1000;cell.inputX=cell.inputZ=0;cell.setVelocity(Vec3d.ZERO);cell.setPosition(0,66.05,0);cell.setYaw(0);
            for(int i=0;i<5;i++){var food=new CellEntity(SporeCell.CELL,world);food.kind(CellEntity.PLANT);food.setPosition(0,66.05,-1.35);world.spawnEntity(food);}
        });
        context.runAtTick(75,()->{
            context.assertTrue(p.progress==50&&p.eaten==5&&p.dna==5,"Food earns progress, DNA and reproduction eligibility: progress="+p.progress+", eaten="+p.eaten+", DNA="+p.dna+", pos="+cell.getPos());
            CellGame.action(player,"{\"type\":\"mate\"}");
            context.assertTrue(CellReproduction.phase(player.getUuid())==0,"Call creates an approaching partner");
            var mate=(CellEntity)world.getEntityById(CellReproduction.partnerId(player.getUuid()));
            context.assertTrue(mate!=null&&mate.kind()==CellEntity.MATE,"Mate is a real entity");
            mate.setPosition(cell.getPos().add(.5,0,0));
        });
        context.runAtTick(120,()->{
            CellGame.openEditor(player);
            var lockedPieces=new ArrayList<>(p.blueprint.pieces());lockedPieces.add(Blueprint.Piece.attachment(0,0,Part.ELECTRIC,0,0,0,0,1));
            int beforeRevision=p.revision,beforeDna=p.dna;var beforeBody=p.blueprint;
            var lockedApply=new JsonObject();lockedApply.addProperty("type","apply");lockedApply.addProperty("revision",p.revision);lockedApply.add("body",SporeCell.GSON.toJsonTree(new Blueprint(lockedPieces)));
            CellGame.action(player,lockedApply.toString());
            context.assertTrue(p.revision==beforeRevision&&p.dna==beforeDna&&p.blueprint.equals(beforeBody)&&CellReproduction.ready(player.getUuid()),"Forged apply cannot buy an undiscovered organ or consume reproduction");
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
            context.assertTrue(restored.blueprint.equals(next)&&restored.generation==1&&restored.history.equals(p.history)&&restored.unlocked.equals(p.unlocked),"NBT preserves attachments, evolution history and discovered parts");
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
            var carrier=organism(world,.3,Part.POISON);CellDiscovery.fromVictim(carrier);carrier.discard();CellDiscovery.collect(player,cell);
            context.assertTrue(p.unlocked.contains(Part.POISON),"Parts from remains can be collected independently of diet");
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
        for(var e:world.getEntitiesByClass(CellEntity.class,new Box(-150,0,-150,150,100,150),e->e!=player&&(e.kind()<CellEntity.MATE||e.discovery())))e.discard();
    }
}
