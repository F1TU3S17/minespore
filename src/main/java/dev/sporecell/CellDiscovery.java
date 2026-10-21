package dev.sporecell;

import dev.sporecell.core.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import java.util.*;

/** Physical discoveries, owned and validated by the server, independent of diet. */
public final class CellDiscovery {
    private CellDiscovery(){}
    private static final Map<UUID,Notice> NOTICES=new HashMap<>();
    public record Notice(Part part,int serial,int until){}
    public static Notice notice(ServerPlayerEntity player){var n=NOTICES.get(player.getUuid());return n!=null&&n.until()>player.getServer().getTicks()?n:null;}
    public static void disconnect(UUID id){NOTICES.remove(id);}
    public static CellEntity drop(ServerWorld world,double x,double z,int level,Part part,boolean meteor){
        if(!CellUnlocks.COLLECTIBLE.contains(part))throw new IllegalArgumentException("Not a collectible organ");
        var entity=new CellEntity(SporeCell.CELL,world);entity.kind(meteor?CellEntity.METEOR:CellEntity.PART_PICKUP);
        entity.loot(part);entity.level(level);entity.setPosition(x,66.05,z);world.spawnEntity(entity);return entity;
    }
    public static void populate(ServerWorld world,CellEntity player){
        var profile=CellSave.get(world.getServer()).profile(player.owner);
        Part next=CellUnlocks.COLLECTIBLE.stream().filter(p->!profile.unlocked.contains(p)&&player.level()>=minimumLevel(p)).findFirst().orElse(null);
        if(next==null)return;
        double range=Math.min(65,28*player.size());
        boolean present=!world.getEntitiesByClass(CellEntity.class,player.getBoundingBox().expand(range),e->e.discovery()&&e.loot()!=null&&!profile.unlocked.contains(e.loot())&&!e.isRemoved()).isEmpty();
        if(!present){
            double angle=world.random.nextDouble()*Math.PI*2,distance=(9+world.random.nextDouble()*6)*Math.min(player.size(),1.65);
            drop(world,player.getX()+Math.cos(angle)*distance,player.getZ()+Math.sin(angle)*distance,player.level(),next,true);
        }
    }
    private static int minimumLevel(Part part){return switch(part){case SPIKE->1;case CILIA,OMNIVORE_MOUTH->2;case POISON->3;case JET->4;case ELECTRIC->5;default->1;};}
    public static void fromVictim(CellEntity victim){
        var save=CellSave.get(victim.getServer());
        var possible=CellUnlocks.COLLECTIBLE.stream().filter(victim::has).filter(part->save.profiles.values().stream().anyMatch(p->p.active&&!p.unlocked.contains(part))).toList();
        if(!possible.isEmpty()&&victim.getWorld().getEntitiesByClass(CellEntity.class,victim.getBoundingBox().expand(48),e->e.discovery()).size()<12)drop((ServerWorld)victim.getWorld(),victim.getX(),victim.getZ(),victim.level(),possible.get(victim.getWorld().random.nextInt(possible.size())),false);
    }
    public static void collect(ServerPlayerEntity player,CellEntity controlled){
        var save=CellSave.get(player.getServer());var profile=save.profile(player.getUuid());
        for(var fragment:controlled.getWorld().getEntitiesByClass(CellEntity.class,controlled.getBoundingBox().expand(controlled.radius()+controlled.size()),e->e.discovery()&&!e.isRemoved())){
            Part part=fragment.loot();if(part==null||profile.unlocked.contains(part))continue;
            boolean touches=controlled.blueprint().pieces().stream().filter(p->!p.mounted()).anyMatch(p->{
                double angle=Math.toRadians(controlled.getYaw()),x=(p.px()*Math.cos(angle)-p.pz()*Math.sin(angle))*controlled.size(),z=(p.px()*Math.sin(angle)+p.pz()*Math.cos(angle))*controlled.size();
                double dx=controlled.getX()+x-fragment.getX(),dz=controlled.getZ()+z-fragment.getZ();return dx*dx+dz*dz<Math.pow(.65*controlled.size()+.3*fragment.size(),2);
            });
            if(!touches)continue;
            fragment.discard();profile.unlocked.add(part);profile.revision++;save.markDirty();
            NOTICES.put(player.getUuid(),new Notice(part,profile.revision,player.getServer().getTicks()+120));
            CellGame.sync(player,false,"Новая часть найдена! Установите её потомку после брачного зова.");
        }
    }
}
