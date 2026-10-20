package dev.sporecell;

import com.google.gson.*;
import dev.sporecell.core.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.entity.MovementType;
import net.minecraft.registry.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import java.util.*;

public final class CellGame {
    private static final Map<UUID,CellEntity> ACTIVE=new HashMap<>();
    private static final Set<UUID> EDITING=new HashSet<>();
    private static final Map<UUID,Long> LAST_EDIT=new HashMap<>();
    private static final Set<MinecraftServer> READY=Collections.newSetFromMap(new WeakHashMap<>());
    private static final double PLANE=66.05;
    private CellGame() {}
    public static void enter(ServerPlayerEntity player) {
        MinecraftServer server=player.getServer(); ServerWorld world=server.getWorld(SporeCell.POND);
        if(world==null) { player.sendMessage(Text.literal("Pond dimension unavailable. Restart the server.")); return; }
        CellSave save=CellSave.get(server); CellSave.Profile p=save.profile(player.getUuid());
        if(!p.active) {
            p.dimension=player.getWorld().getRegistryKey().getValue().toString();
            p.x=player.getX();p.y=player.getY();p.z=player.getZ();p.yaw=player.getYaw();p.pitch=player.getPitch();
            p.gameMode=player.interactionManager.getGameMode().getId();p.active=true;
        }
        prepare(world);
        CellEntity previous=ACTIVE.remove(player.getUuid()); if(previous!=null) previous.discard();
        CellEntity cell=spawn(world,CellEntity.PLAYER,0,0,p.blueprint); cell.owner=player.getUuid();
        cell.health(Math.max(1,Math.min(p.health,(float)p.blueprint.stats(Parts.SPECS).health())));
        ACTIVE.put(player.getUuid(),cell);
        player.teleport(world,0,PLANE+2,0,0,0); player.changeGameMode(GameMode.SPECTATOR); player.setCameraEntity(cell);
        save.markDirty(); sync(player,false,"");
        player.sendMessage(Text.literal("Cell stage: WASD move, G editor, /cell leave to return."));
    }
    public static void leave(ServerPlayerEntity player) {
        CellSave save=CellSave.get(player.getServer()); CellSave.Profile p=save.profile(player.getUuid());
        if(!p.active) return;
        CellEntity cell=ACTIVE.remove(player.getUuid());
        if(cell!=null) { p.health=cell.health();cell.discard(); }
        EDITING.remove(player.getUuid());LAST_EDIT.remove(player.getUuid());
        player.setCameraEntity(player);
        ServerWorld back=player.getServer().getWorld(RegistryKey.of(RegistryKeys.WORLD,Identifier.of(p.dimension)));
        if(back==null) back=player.getServer().getOverworld();
        player.teleport(back,p.x,p.y,p.z,p.yaw,p.pitch); player.changeGameMode(GameMode.byId(p.gameMode));
        p.active=false; save.markDirty(); sync(player,false,"");
    }
    public static void disconnect(ServerPlayerEntity player) {
        CellEntity cell=ACTIVE.remove(player.getUuid());
        if(cell!=null) {
            CellSave save=CellSave.get(player.getServer());save.profile(player.getUuid()).health=cell.health();save.markDirty();cell.discard();
        }
        EDITING.remove(player.getUuid());LAST_EDIT.remove(player.getUuid());
    }
    public static void openEditor(ServerPlayerEntity player) {
        CellEntity cell=ACTIVE.get(player.getUuid()); if(cell==null) return;
        EDITING.add(player.getUuid());cell.inputX=cell.inputZ=0;cell.setVelocity(Vec3d.ZERO);sync(player,true,"");
    }
    public static void action(ServerPlayerEntity player,String json) {
        CellEntity cell=ACTIVE.get(player.getUuid());if(cell==null || cell.isRemoved()) return;
        try {
            JsonObject obj=JsonParser.parseString(json).getAsJsonObject();String type=obj.get("type").getAsString();
            if(type.equals("move")) {
                double x=obj.get("x").getAsDouble(),z=obj.get("z").getAsDouble();
                if(!Double.isFinite(x)||!Double.isFinite(z)) return;
                cell.inputX=MathHelper.clamp(x,-1,1);cell.inputZ=MathHelper.clamp(z,-1,1);cell.inputAge=0;return;
            }
            if(type.equals("editor")) { openEditor(player);return; }
            if(type.equals("close")) { EDITING.remove(player.getUuid());sync(player,false,"");return; }
            if(!type.equals("apply") || !EDITING.contains(player.getUuid())) return;
            long now=player.getServerWorld().getTime();
            if(now-LAST_EDIT.getOrDefault(player.getUuid(),-100L)<5) return;
            LAST_EDIT.put(player.getUuid(),now);
            CellSave save=CellSave.get(player.getServer());CellSave.Profile p=save.profile(player.getUuid());
            if(obj.get("revision").getAsInt()!=p.revision) { sync(player,true,"Progress changed. Reopen the editor.");return; }
            Blueprint next=SporeCell.GSON.fromJson(obj.get("body"),Blueprint.class);next.validate();
            int cost=Blueprint.purchaseCost(p.blueprint,next,Parts.SPECS);
            if(cost>p.dna) { sync(player,true,"Not enough DNA");return; }
            double oldMax=p.blueprint.stats(Parts.SPECS).health(),newMax=next.stats(Parts.SPECS).health();
            p.health=(float)Math.min(newMax,cell.health()+Math.max(0,newMax-oldMax));
            p.dna-=cost;p.blueprint=next;p.revision++;cell.blueprint(next);cell.health(p.health);
            save.markDirty();EDITING.remove(player.getUuid());sync(player,false,"Evolution applied");
        } catch(RuntimeException e) { sync(player,true,"Invalid construction: "+e.getMessage()); }
    }
    private static void sync(ServerPlayerEntity player,boolean editor,String message) {
        CellSave.Profile p=CellSave.get(player.getServer()).profile(player.getUuid());
        JsonObject state=new JsonObject();state.addProperty("active",p.active);state.addProperty("editor",editor);
        state.addProperty("editing",EDITING.contains(player.getUuid()));
        CellEntity controlled=ACTIVE.get(player.getUuid());state.addProperty("entityId",controlled==null?-1:controlled.getId());
        state.addProperty("dna",p.dna);state.addProperty("health",p.health);state.addProperty("revision",p.revision);
        state.addProperty("eaten",p.eaten);state.addProperty("deaths",p.deaths);state.addProperty("message",message);
        state.add("body",SporeCell.GSON.toJsonTree(p.blueprint));state.add("specs",SporeCell.GSON.toJsonTree(Parts.SPECS));
        ServerPlayNetworking.send(player,new CellPackets.State(state.toString()));
    }
    public static void serverTick(MinecraftServer server) {
        CellSave save=CellSave.get(server);
        for(ServerPlayerEntity player:server.getPlayerManager().getPlayerList()) {
            CellSave.Profile p=save.profiles.get(player.getUuid());if(p==null || !p.active) continue;
            CellEntity cell=ACTIVE.get(player.getUuid());
            if(cell==null || cell.isRemoved()) { enter(player);continue; }
            if(player.getWorld()!=cell.getWorld()) { leave(player);continue; }
            if(player.getCameraEntity()!=cell) player.setCameraEntity(cell);
            if(player.squaredDistanceTo(cell)>64) player.teleport((ServerWorld)cell.getWorld(),cell.getX(),PLANE+2,cell.getZ(),0,0);
            if(server.getTicks()%20==0) { p.health=cell.health();save.markDirty();sync(player,false,""); }
        }
        ServerWorld world=server.getWorld(SporeCell.POND);
        if(world==null || ACTIVE.isEmpty() || server.getTicks()%100!=0) return;
        List<CellEntity> all=world.getEntitiesByClass(CellEntity.class,new Box(-50,60,-50,50,80,50),e->true);
        int plants=0,meat=0,npcs=0;
        for(CellEntity e:all) { if(e.kind()==CellEntity.PLANT)plants++; else if(e.kind()==CellEntity.MEAT)meat++; else if(e.owner==null)npcs++; }
        for(int i=plants;i<45;i++) randomSpawn(world,CellEntity.PLANT);
        for(int i=meat;i<12;i++) randomSpawn(world,CellEntity.MEAT);
        for(int i=npcs;i<9;i++) randomSpawn(world,i%3==0?CellEntity.HUNTER:CellEntity.GRAZER);
    }
    private static void prepare(ServerWorld world) {
        if(!READY.add(world.getServer())) return;
        // Marker survives world reloads. Arena initialization is confined to this dimension.
        if(world.getBlockState(new BlockPos(0,63,0)).isOf(Blocks.SEA_LANTERN)) return;
        BlockPos.Mutable pos=new BlockPos.Mutable();
        for(int x=-48;x<=48;x++)for(int z=-48;z<=48;z++) {
            world.setBlockState(pos.set(x,64,z),Blocks.PRISMARINE.getDefaultState(),2);
            world.setBlockState(pos.set(x,65,z),Blocks.WATER.getDefaultState(),2);
            if(Math.abs(x)==48||Math.abs(z)==48) for(int y=65;y<=68;y++)world.setBlockState(pos.set(x,y,z),Blocks.DARK_PRISMARINE.getDefaultState(),2);
        }
        for(int x=-30;x<=30;x+=15)for(int z=-30;z<=30;z+=15) {
            if(Math.abs(x)<8&&Math.abs(z)<8) continue;
            for(int y=66;y<=67;y++)world.setBlockState(pos.set(x,y,z),Blocks.MOSSY_COBBLESTONE.getDefaultState(),2);
        }
        world.setBlockState(new BlockPos(0,63,0),Blocks.SEA_LANTERN.getDefaultState(),2);
        for(int i=0;i<45;i++)randomSpawn(world,CellEntity.PLANT);
        for(int i=0;i<12;i++)randomSpawn(world,CellEntity.MEAT);
        for(int i=0;i<9;i++)randomSpawn(world,i%3==0?CellEntity.HUNTER:CellEntity.GRAZER);
    }
    private static CellEntity spawn(ServerWorld world,int kind,double x,double z,Blueprint body) {
        CellEntity e=new CellEntity(SporeCell.CELL,world);e.kind(kind);e.blueprint(body);
        e.refreshPositionAndAngles(x,PLANE,z,0,0);e.health((float)body.stats(Parts.SPECS).health());world.spawnEntity(e);return e;
    }
    private static void randomSpawn(ServerWorld world,int kind) {
        double x=world.random.nextBetween(-42,42),z=world.random.nextBetween(-42,42);
        if(world.getBlockState(BlockPos.ofFloored(x,PLANE,z)).isSolidBlock(world,BlockPos.ofFloored(x,PLANE,z)))return;
        Blueprint body=Blueprint.starter();
        if(kind==CellEntity.HUNTER)body=new Blueprint(List.of(new Blueprint.Piece(0,0,Part.CORE,0),
                new Blueprint.Piece(0,-1,Part.CARNIVORE_MOUTH,0),new Blueprint.Piece(0,1,Part.FLAGELLUM,2),
                new Blueprint.Piece(1,0,Part.ARMOR,1),new Blueprint.Piece(-1,0,Part.SPIKE,3)));
        spawn(world,kind,x,z,body);
    }
    public static void tick(CellEntity e) {
        if(e.getWorld().getRegistryKey()!=SporeCell.POND) return;
        ServerWorld world=(ServerWorld)e.getWorld();
        if(e.owner!=null && ACTIVE.get(e.owner)!=e) { e.discard();return; }
        if(e.hurtCooldown>0)e.hurtCooldown--;
        if(e.food()) return;
        if(e.owner!=null && EDITING.contains(e.owner))return;
        Blueprint.Stats stats=e.blueprint().stats(Parts.SPECS);
        double x=0,z=0;
        if(e.owner!=null) {
            e.inputAge++;if(e.inputAge<=12) { x=e.inputX;z=e.inputZ; }
        } else {
            if(e.age%15==0) think(e,stats);
            x=e.inputX;z=e.inputZ;
        }
        double length=Math.hypot(x,z);
        if(length>.001) {
            double target=Math.toDegrees(Math.atan2(x,-z));e.setYaw((float)Motion.steer(e.getYaw(),target,stats.turn()));
        }
        double angle=Math.toRadians(e.getYaw());
        Vec3d desired=length>.001?new Vec3d(Math.sin(angle),0,-Math.cos(angle)).multiply(stats.speed()*Math.min(1,length)):Vec3d.ZERO;
        Vec3d velocity=e.getVelocity().multiply(.78).add(desired.multiply(.22));
        // Check every occupied cube, including off-center appendages, against obstacles.
        if(!clear(e,e.getX()+velocity.x,e.getZ()+velocity.z)) velocity=Vec3d.ZERO;
        e.setVelocity(velocity);e.move(MovementType.SELF,velocity);e.setPosition(e.getX(),PLANE,e.getZ());
        if(e.age%4==0) interact(e,stats);
    }
    private static boolean clear(CellEntity e,double x,double z) {
        if(Math.abs(x)>46||Math.abs(z)>46)return false;
        for(Blueprint.Piece p:e.blueprint().pieces()) {
            Vec3d offset=offset(e,p);Box box=new Box(x+offset.x-.29,PLANE,z+offset.z-.29,x+offset.x+.29,PLANE+.45,z+offset.z+.29);
            if(!e.getWorld().isSpaceEmpty(e,box))return false;
        }
        for(CellEntity other:e.getWorld().getEntitiesByClass(CellEntity.class,e.getBoundingBox().expand(8),o->o!=e&&!o.food()&&!o.isRemoved())) {
            for(Blueprint.Piece p:e.blueprint().pieces())for(Blueprint.Piece q:other.blueprint().pieces()) {
                Vec3d a=offset(e,p),b=offset(other,q);
                double dx=x+a.x-other.getX()-b.x,dz=z+a.z-other.getZ()-b.z;
                double oldX=e.getX()+a.x-other.getX()-b.x,oldZ=e.getZ()+a.z-other.getZ()-b.z;
                // Permit separation after a spawn or editor change creates an overlap.
                if(dx*dx+dz*dz<.34 && dx*dx+dz*dz<oldX*oldX+oldZ*oldZ)return false;
            }
        }
        return true;
    }
    private static Vec3d offset(CellEntity e,Blueprint.Piece p) {
        double a=Math.toRadians(e.getYaw()),c=Math.cos(a),s=Math.sin(a);
        return new Vec3d((p.x()*c-p.z()*s)*.65,0,(p.x()*s+p.z()*c)*.65);
    }
    private static void think(CellEntity e,Blueprint.Stats stats) {
        List<CellEntity> nearby=e.getWorld().getEntitiesByClass(CellEntity.class,e.getBoundingBox().expand(stats.sight()),
                other->other!=e && !other.isRemoved() && (other.owner==null||!EDITING.contains(other.owner)));
        CellEntity threat=nearby.stream().filter(o->!o.food()&&o.blueprint().stats(Parts.SPECS).carnivore()
                &&o.blueprint().stats(Parts.SPECS).health()>stats.health()).min(Comparator.comparingDouble(e::squaredDistanceTo)).orElse(null);
        CellEntity target;
        if(threat!=null) { e.behavior="flee"; e.inputX=e.getX()-threat.getX();e.inputZ=e.getZ()-threat.getZ();return; }
        target=nearby.stream().filter(o->(o.kind()==CellEntity.PLANT&&stats.herbivore())||(o.kind()==CellEntity.MEAT&&stats.carnivore())
                ||(!o.food()&&stats.carnivore()&&o.blueprint().stats(Parts.SPECS).health()<stats.health()))
                .min(Comparator.comparingDouble(e::squaredDistanceTo)).orElse(null);
        if(target!=null) { e.behavior=target.food()?"feed":"hunt";e.inputX=target.getX()-e.getX();e.inputZ=target.getZ()-e.getZ(); }
        else if(e.age%60==0 || (e.inputX==0&&e.inputZ==0)) {
            e.behavior="wander";double a=e.getWorld().random.nextDouble()*Math.PI*2;
            e.inputX=Math.abs(e.getX())>38?-e.getX():Math.sin(a);e.inputZ=Math.abs(e.getZ())>38?-e.getZ():Math.cos(a);
        }
    }
    private static void interact(CellEntity e,Blueprint.Stats stats) {
        for(CellEntity other:e.getWorld().getEntitiesByClass(CellEntity.class,e.getBoundingBox().expand(7),o->o!=e&&!o.isRemoved())) {
            if(other.owner!=null && EDITING.contains(other.owner))continue;
            for(Blueprint.Piece p:e.blueprint().pieces()) {
                Vec3d pos=e.getPos().add(offset(e,p));
                Vec3d target=other.getPos();
                if(!other.food()) {
                    double nearest=Double.MAX_VALUE;
                    for(Blueprint.Piece q:other.blueprint().pieces()) {
                        Vec3d candidate=other.getPos().add(offset(other,q));double distance=candidate.squaredDistanceTo(pos);
                        if(distance<nearest) { nearest=distance;target=candidate; }
                    }
                }
                double dx=target.x-pos.x,dz=target.z-pos.z;
                if(dx*dx+dz*dz>1.05 || !Motion.faces(dx,dz,p.direction(),e.getYaw()))continue;
                if(other.food()) {
                    if((other.kind()==CellEntity.PLANT&&p.part()==Part.HERBIVORE_MOUTH)||(other.kind()==CellEntity.MEAT&&p.part()==Part.CARNIVORE_MOUTH)) {
                        other.discard();e.health(Math.min((float)stats.health(),e.health()+2));reward(e,other.kind()==CellEntity.PLANT?2:3);break;
                    }
                } else if(p.part()==Part.SPIKE || p.part()==Part.CARNIVORE_MOUTH) {
                    // Contact with any target cube, rather than only the nucleus.
                    hit(e,other,(float)Parts.SPECS.get(p.part()).damage());break;
                }
            }
        }
    }
    private static void reward(CellEntity e,int dna) {
        if(e.owner==null)return;
        CellSave save=CellSave.get(e.getServer());CellSave.Profile p=save.profile(e.owner);
        p.dna=Math.min(100000,p.dna+dna);p.eaten++;p.health=e.health();save.markDirty();
    }
    private static void hit(CellEntity attacker,CellEntity target,float damage) {
        if(target.hurtCooldown>0)return;
        target.hurtCooldown=15;target.health(target.health()-damage);
        if(target.health()>0)return;
        if(target.owner==null) {
            spawn((ServerWorld)target.getWorld(),CellEntity.MEAT,target.getX(),target.getZ(),Blueprint.starter());target.discard();reward(attacker,3);
        } else {
            CellSave save=CellSave.get(target.getServer());CellSave.Profile p=save.profile(target.owner);
            p.deaths++;p.health=(float)p.blueprint.stats(Parts.SPECS).health();target.health(p.health);target.hurtCooldown=80;
            target.setPosition(0,PLANE,0);target.setVelocity(Vec3d.ZERO);save.markDirty();
            ServerPlayerEntity player=target.getServer().getPlayerManager().getPlayer(target.owner);
            if(player!=null)sync(player,false,"Cell died; respawned at the center. DNA retained.");
        }
    }
}
