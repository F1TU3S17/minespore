package dev.sporecell;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import java.util.*;

/** Server-owned mating lifecycle; UI cannot grant itself an editing session. */
public final class CellReproduction {
    private CellReproduction(){}
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static final class Session {
        CellEntity partner,egg;
        int phase,ticks;
        Session(CellEntity partner){this.partner=partner;}
    }
    static boolean owns(CellEntity entity){return SESSIONS.values().stream().anyMatch(s->s.partner==entity||s.egg==entity);}
    public static int phase(UUID id){var s=SESSIONS.get(id);return s==null?-1:s.phase;}
    public static boolean ready(UUID id){return phase(id)==2;}
    public static int partnerId(UUID id){var s=SESSIONS.get(id);return s==null?-1:s.partner.getId();}
    public static int foodNeeded(CellSave.Profile p){return Math.max(0,5-(p.eaten-p.lastBreedEaten));}
    public static void call(ServerPlayerEntity player){
        if(SESSIONS.containsKey(player.getUuid())){CellGame.sync(player,false,"Партнёр уже откликается на зов.");return;}
        var cell=CellGame.controlled(player);if(cell==null)return;
        var profile=CellSave.get(player.getServer()).profile(player.getUuid());
        if(foodNeeded(profile)>0){CellGame.sync(player,false,"До размножения соберите ещё "+foodNeeded(profile)+" порций пищи.");return;}
        var partner=new CellEntity(SporeCell.CELL,player.getServerWorld());partner.kind(CellEntity.MATE);
        partner.blueprint(profile.blueprint);partner.level(cell.level());
        double angle=player.getServerWorld().random.nextDouble()*Math.PI*2;
        partner.refreshPositionAndAngles(cell.getX()+Math.cos(angle)*6*cell.size(),cell.getY(),cell.getZ()+Math.sin(angle)*6*cell.size(),0,0);
        player.getServerWorld().spawnEntity(partner);SESSIONS.put(player.getUuid(),new Session(partner));
        CellGame.sync(player,false,"Брачный зов! Партнёр плывёт к вашей клетке.");
    }
    public static void tick(MinecraftServer server){
        for(var entry:new ArrayList<>(SESSIONS.entrySet())){
            var player=server.getPlayerManager().getPlayer(entry.getKey());
            if(player==null){cancel(entry.getKey());continue;}
            var cell=CellGame.controlled(player);var s=entry.getValue();
            if(cell==null||s.partner.isRemoved()||cell.getWorld()!=s.partner.getWorld()){cancel(entry.getKey());continue;}
            if(s.phase==0){
                Vec3d delta=cell.getPos().subtract(s.partner.getPos());
                if(delta.horizontalLength()<1.6*cell.size()){
                    s.phase=1;s.ticks=0;CellGame.protect(player.getUuid(),true);cell.setVelocity(Vec3d.ZERO);
                    s.egg=new CellEntity(SporeCell.CELL,player.getServerWorld());s.egg.kind(CellEntity.EGG);s.egg.level(cell.level());
                    s.egg.setPosition(cell.getX(),cell.getY(),cell.getZ());player.getServerWorld().spawnEntity(s.egg);
                    CellGame.sync(player,false,"Размножение: формируется яйцо нового поколения.");
                }else{
                    Vec3d velocity=delta.normalize().multiply(.16*cell.size());
                    s.partner.setPosition(s.partner.getPos().add(velocity));
                    s.partner.setYaw((float)Math.toDegrees(Math.atan2(velocity.x,-velocity.z)));
                }
            }else if(s.phase==1&&++s.ticks>=40){
                s.phase=2;CellGame.sync(player,false,"Потомок готов к эволюции. Измените его в редакторе.");
            }
        }
    }
    public static void finish(ServerPlayerEntity player){
        var profile=CellSave.get(player.getServer()).profile(player.getUuid());
        profile.generation++;profile.lastBreedEaten=profile.eaten;
        if(profile.history.size()>=64)profile.history.removeFirst();
        profile.history.add(new CellSave.EvolutionEvent(profile.generation,profile.progress,profile.plantFood,profile.animalFood));
        CellSave.get(player.getServer()).markDirty();cancel(player.getUuid());
    }
    public static void cancel(UUID id){
        var s=SESSIONS.remove(id);
        if(s!=null){s.partner.discard();if(s.egg!=null)s.egg.discard();CellGame.protect(id,false);}
    }
}
