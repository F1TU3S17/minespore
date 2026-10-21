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
    private record CombatTarget(int id,int level,float health,float damage,int serial,int until){}
    private static final Map<UUID,CombatTarget> TARGETS=new HashMap<>();
    private static int attackSerial;
    private static final Map<UUID,CellEntity> ACTIVE=new HashMap<>();
    private static final Set<UUID> EDITING=new HashSet<>();
    private static final Map<UUID,Long> LAST_EDIT=new HashMap<>();
    private static final Set<MinecraftServer> READY=Collections.newSetFromMap(new WeakHashMap<>());
    private static final double PLANE=66.05;
    private CellGame() {}
    public static void startMode(ServerPlayerEntity player,Evolution.Diet diet) {
        CellSave save=CellSave.get(player.getServer());CellSave.Profile p=save.profile(player.getUuid());
        if(!p.started && p.revision==0 && p.eaten==0) { p.blueprint=Blueprint.starter(diet);p.health=6; }
        p.started=true;
        if(player.getServer().getSaveProperties().getLevelName().startsWith("Spore Cell"))save.dedicatedMode=true;
        if(!ACTIVE.containsKey(player.getUuid()))enter(player);else sync(player,false,"");
        save.markDirty();
    }
    public static void enter(ServerPlayerEntity player) {
        MinecraftServer server=player.getServer(); ServerWorld world=server.getWorld(SporeCell.POND);
        if(world==null) { player.sendMessage(Text.literal("Измерение клетки недоступно. Перезапустите мир.")); return; }
        CellSave save=CellSave.get(server); CellSave.Profile p=save.profile(player.getUuid());
        if(!p.active) {
            p.dimension=player.getWorld().getRegistryKey().getValue().toString();
            p.x=player.getX();p.y=player.getY();p.z=player.getZ();p.yaw=player.getYaw();p.pitch=player.getPitch();
            p.gameMode=player.interactionManager.getGameMode().getId();p.active=true;
        }
        prepare(world);
        CellEntity previous=ACTIVE.remove(player.getUuid()); if(previous!=null) previous.discard();
        CellEntity cell=spawn(world,CellEntity.PLAYER,0,0,p.blueprint); cell.owner=player.getUuid();
        p.started=true;cell.level(Evolution.level(p.progress));
        cell.health(Math.max(1,Math.min(p.health,(float)p.blueprint.stats(Parts.SPECS).health())));
        ACTIVE.put(player.getUuid(),cell);
        player.teleport(world,0,PLANE+2,0,0,0); player.changeGameMode(GameMode.SPECTATOR); player.setCameraEntity(cell);
        save.markDirty();populate(world); sync(player,false,"");
        player.sendMessage(Text.literal("ЛКМ — плыть к курсору · G — брачный зов · V — эволюция"),true);
    }
    public static void leave(ServerPlayerEntity player) {
        CellSave save=CellSave.get(player.getServer()); CellSave.Profile p=save.profile(player.getUuid());
        if(!p.active) return;
        CellReproduction.cancel(player.getUuid());
        if(save.dedicatedMode){player.sendMessage(Text.literal("Это мир стадии клетки. Чтобы закончить игру, выберите «Сохранить и выйти» в меню паузы."));return;}
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
        CellDiscovery.disconnect(player.getUuid());
        CellReproduction.cancel(player.getUuid());TARGETS.remove(player.getUuid());
        CellEntity cell=ACTIVE.remove(player.getUuid());
        if(cell!=null) {
            CellSave save=CellSave.get(player.getServer());save.profile(player.getUuid()).health=cell.health();save.markDirty();cell.discard();
        }
        EDITING.remove(player.getUuid());LAST_EDIT.remove(player.getUuid());
    }
    static CellEntity controlled(ServerPlayerEntity player){return ACTIVE.get(player.getUuid());}
    static void protect(UUID id,boolean protect){if(protect)EDITING.add(id);else EDITING.remove(id);}
    public static void openEditor(ServerPlayerEntity player) {
        if(!CellReproduction.ready(player.getUuid())){sync(player,false,"Редактор доступен после размножения. Брачный зов: G.");return;}
        CellEntity cell=ACTIVE.get(player.getUuid()); if(cell==null) return;
        EDITING.add(player.getUuid());cell.inputX=cell.inputZ=0;cell.setVelocity(Vec3d.ZERO);sync(player,true,"");
    }
    public static void action(ServerPlayerEntity player,String json) {
        try {
            JsonObject start=JsonParser.parseString(json).getAsJsonObject();
            if(start.has("type")&&start.get("type").getAsString().equals("start")) {
                Evolution.Diet diet=Evolution.Diet.valueOf(start.get("diet").getAsString());
                if(diet==Evolution.Diet.OMNIVORE)return;
                startMode(player,diet);return;
            }
        } catch(RuntimeException ex){return;}
        CellEntity cell=ACTIVE.get(player.getUuid());if(cell==null || cell.isRemoved()) return;
        try {
            JsonObject obj=JsonParser.parseString(json).getAsJsonObject();String type=obj.get("type").getAsString();
            if(type.equals("move")) {
                double x=obj.get("x").getAsDouble(),z=obj.get("z").getAsDouble();
                if(!Double.isFinite(x)||!Double.isFinite(z)) return;
                cell.inputX=MathHelper.clamp(x,-1,1);cell.inputZ=MathHelper.clamp(z,-1,1);cell.inputAge=0;return;
            }
            if(type.equals("mate")){CellReproduction.call(player);return;}
            if(type.equals("editor")) { openEditor(player);return; }
            if(type.equals("close")) { CellReproduction.cancel(player.getUuid());EDITING.remove(player.getUuid());sync(player,false,"");return; }
            if(!type.equals("apply") || !EDITING.contains(player.getUuid()) || !CellReproduction.ready(player.getUuid())) return;
            long now=player.getServerWorld().getTime();
            if(now-LAST_EDIT.getOrDefault(player.getUuid(),-100L)<5) return;
            LAST_EDIT.put(player.getUuid(),now);
            CellSave save=CellSave.get(player.getServer());CellSave.Profile p=save.profile(player.getUuid());
            if(obj.get("revision").getAsInt()!=p.revision) { sync(player,true,"Прогресс изменился. Откройте редактор заново.");return; }
            Blueprint next=SporeCell.GSON.fromJson(obj.get("body"),Blueprint.class);next.validate();
            int cost=Blueprint.purchaseCost(p.blueprint,next,Parts.SPECS);
            if(!CellUnlocks.permits(p.unlocked,next)) {
                sync(player,true,"Сначала найдите эту часть в мире: в метеорите или останках клетки.");return;
            }
            if(cost>p.dna) { sync(player,true,"Недостаточно ДНК");return; }
            double oldMax=p.blueprint.stats(Parts.SPECS).health(),newMax=next.stats(Parts.SPECS).health();
            p.health=(float)Math.min(newMax,cell.health()+Math.max(0,newMax-oldMax));
            p.dna-=cost;p.blueprint=next;p.revision++;cell.blueprint(next);cell.health(p.health);
            CellReproduction.finish(player);cell.inputX=cell.inputZ=0;
            save.markDirty();EDITING.remove(player.getUuid());sync(player,false,"Новое поколение: "+p.generation+". Конструкция потомка сохранена.");
        } catch(RuntimeException e) { sync(player,true,"Недопустимая конструкция: "+e.getMessage()); }
    }
    static void sync(ServerPlayerEntity player,boolean editor,String message) {
        CellSave.Profile p=CellSave.get(player.getServer()).profile(player.getUuid());
        JsonObject state=new JsonObject();state.addProperty("active",p.active);state.addProperty("editor",editor);
        state.addProperty("editing",EDITING.contains(player.getUuid()));
        CellEntity controlled=ACTIVE.get(player.getUuid());state.addProperty("entityId",controlled==null?-1:controlled.getId());
        state.addProperty("dna",p.dna);state.addProperty("health",p.health);state.addProperty("revision",p.revision);
        state.addProperty("eaten",p.eaten);state.addProperty("deaths",p.deaths);state.addProperty("message",message);
        state.addProperty("generation",p.generation);state.add("history",SporeCell.GSON.toJsonTree(p.history));
        state.add("unlocked",SporeCell.GSON.toJsonTree(p.unlocked));
        var discovery=CellDiscovery.notice(player);state.add("discovery",SporeCell.GSON.toJsonTree(discovery));
        state.addProperty("mating",CellReproduction.phase(player.getUuid()));state.addProperty("mateId",CellReproduction.partnerId(player.getUuid()));
        state.addProperty("breedNeeded",CellReproduction.foodNeeded(p));state.addProperty("evolution",CellReproduction.ready(player.getUuid()));
        state.addProperty("progress",p.progress);state.addProperty("level",Evolution.level(p.progress));
        state.addProperty("plantFood",p.plantFood);state.addProperty("animalFood",p.animalFood);
        var capabilities=p.blueprint.stats(Parts.SPECS);
        state.addProperty("diet",Evolution.diet(capabilities.herbivore(),capabilities.carnivore()).name());
        var target=TARGETS.get(player.getUuid());
        if(target!=null&&target.until()>player.getServer().getTicks()){
            JsonObject combat=new JsonObject();combat.addProperty("id",target.id());combat.addProperty("level",target.level());
            float hp=target.health();var entity=player.getServerWorld().getEntityById(target.id());if(entity instanceof CellEntity live)hp=live.health();
            combat.addProperty("health",Math.max(0,hp));combat.addProperty("damage",target.damage());combat.addProperty("serial",target.serial());state.add("target",combat);
        }else state.add("target",com.google.gson.JsonNull.INSTANCE);
        state.add("body",SporeCell.GSON.toJsonTree(p.blueprint));state.add("specs",SporeCell.GSON.toJsonTree(Parts.SPECS));
        ServerPlayNetworking.send(player,new CellPackets.State(state.toString()));
    }
    public static void serverTick(MinecraftServer server) {
        CellReproduction.tick(server);
        CellSave save=CellSave.get(server);
        for(ServerPlayerEntity player:server.getPlayerManager().getPlayerList()) {
            CellSave.Profile p=save.profiles.get(player.getUuid());if(p==null)continue;
            if(save.dedicatedMode && p.started && !p.active){enter(player);continue;}
            if(!p.active)continue;
            CellEntity cell=ACTIVE.get(player.getUuid());
            if(cell==null || cell.isRemoved()) { enter(player);continue; }
            if(player.getWorld()!=cell.getWorld()) { leave(player);continue; }
            if(player.getCameraEntity()!=cell) player.setCameraEntity(cell);
            if(player.squaredDistanceTo(cell)>64) player.teleport((ServerWorld)cell.getWorld(),cell.getX(),PLANE+2,cell.getZ(),0,0);
            if(!EDITING.contains(player.getUuid()))CellDiscovery.collect(player,cell);
            if(server.getTicks()%20==0) { p.health=cell.health();save.markDirty();sync(player,false,""); }
        }
        ServerWorld world=server.getWorld(SporeCell.POND);
        if(world!=null && !ACTIVE.isEmpty() && server.getTicks()%40==0)populate(world);
    }
    private static void prepare(ServerWorld world) {
        if(!READY.add(world.getServer()))return;
        CellSave save=CellSave.get(world.getServer());
        if(save.environmentVersion<2) {
            // Migrate only the old prototype arena inside our own dimension.
            if(world.getBlockState(new BlockPos(0,63,0)).isOf(Blocks.SEA_LANTERN)) {
                BlockPos.Mutable pos=new BlockPos.Mutable();
                for(int x=-48;x<=48;x++)for(int z=-48;z<=48;z++)for(int y=63;y<=68;y++)
                    world.setBlockState(pos.set(x,y,z),Blocks.AIR.getDefaultState(),2);
            }
            save.environmentVersion=2;save.markDirty();
        }
    }
    private static void populate(ServerWorld world) {
        List<CellEntity> players=ACTIVE.values().stream().filter(e->e.getWorld()==world&&!e.isRemoved()).toList();
        if(players.isEmpty())return;
        List<CellEntity> all=new ArrayList<>();
        for(var entity:world.iterateEntities())if(entity instanceof CellEntity cell && cell.owner==null && (cell.kind()<CellEntity.MATE||cell.discovery()))all.add(cell);
        for(CellEntity cell:all)if(players.stream().noneMatch(p->p.squaredDistanceTo(cell)<Math.pow(Math.min(80,58*p.size()),2)))cell.discard();
        for(CellEntity player:players) {
            CellDiscovery.populate(world,player);
            int[] count=new int[7];
            for(CellEntity cell:all)if(cell.kind()<CellEntity.MATE&&!cell.isRemoved()&&player.squaredDistanceTo(cell)<Math.pow(Math.min(64,45*player.size()),2))count[cell.kind()]++;
            int[] quotas={0,10,5,50,16,7,7};
            for(int kind=1;kind<quotas.length;kind++)for(int i=count[kind];i<quotas[kind];i++)randomSpawn(world,kind,player);
        }
    }
    private static CellEntity spawn(ServerWorld world,int kind,double x,double z,Blueprint body) {
        CellEntity e=new CellEntity(SporeCell.CELL,world);e.kind(kind);e.blueprint(body);
        e.refreshPositionAndAngles(x,PLANE,z,0,0);e.health((float)body.stats(Parts.SPECS).health());world.spawnEntity(e);return e;
    }
    private static void randomSpawn(ServerWorld world,int kind,CellEntity player) {
        double scale=player.size(),angle=world.random.nextDouble()*Math.PI*2;
        double distance=(kind==CellEntity.PLANT||kind==CellEntity.MEAT?3+world.random.nextDouble()*24:9+world.random.nextDouble()*29)*Math.min(scale,1.65);
        double x=player.getX()+Math.cos(angle)*distance,z=player.getZ()+Math.sin(angle)*distance;
        int variant=world.random.nextInt(CellSpecies.COUNT);
        Blueprint body=CellSpecies.create(variant,kind==CellEntity.HUNTER);
        if(kind==CellEntity.ROCK)body=new Blueprint(List.of(new Blueprint.Piece(0,0,Part.CORE,0)));
        CellEntity spawned=spawn(world,kind,x,z,body);
        int delta=kind==CellEntity.HUNTER?world.random.nextBetween(0,3):world.random.nextBetween(-2,2);
        spawned.level(Math.clamp(player.level()+delta,0,10));
        if(spawned.food())spawned.level(player.level());
        if(kind==CellEntity.BACKGROUND) {spawned.level(Math.min(10,player.level()+4));spawned.setPosition(x,PLANE-5*scale,z);}
        if(kind==CellEntity.ROCK)spawned.level(Math.min(10,player.level()+2));
        spawned.setYaw(world.random.nextFloat()*360);
    }
    public static void tick(CellEntity e) {
        if(e.getWorld().getRegistryKey()!=SporeCell.POND) return;
        ServerWorld world=(ServerWorld)e.getWorld();
        if(e.owner!=null && ACTIVE.get(e.owner)!=e) { e.discard();return; }
        if(e.discovery())return;
        if(e.hurtCooldown>0)e.hurtCooldown--;
        if(e.kind()==CellEntity.BACKGROUND) {
            double t=(e.age+e.getId()*17)*.008;
            e.setPosition(e.getX()+Math.cos(t)*.012*e.size(),e.getY(),e.getZ()+Math.sin(t)*.012*e.size());return;
        }
        if(e.kind()==CellEntity.POISON_CLOUD){poisonCloud(e);return;}
        if(e.kind()>=CellEntity.MATE){if(!CellReproduction.owns(e))e.discard();return;}
        if(e.food()||e.kind()==CellEntity.ROCK)return;
        if(e.owner!=null && EDITING.contains(e.owner))return;
        if(e.poisonTicks>0){
            e.poisonTicks--;
            if(e.poisonTicks%20==0&&!e.has(Part.POISON)){
                var source=world.getEntity(e.poisonSource);hit(source instanceof CellEntity cell?cell:null,e,(float)Parts.SPECS.get(Part.POISON).damage());
                if(e.isRemoved())return;
            }
        }
        if(e.poisonCooldown>0)e.poisonCooldown--;if(e.electricCooldown>0)e.electricCooldown--;if(e.electricTicks>0)e.electricTicks--;
        e.effects((e.poisonTicks>0?1:0)|(e.electricTicks>0?2:0)|(e.has(Part.JET)&&e.age%60<12?4:0));
        if(e.stunTicks>0){e.stunTicks--;e.setVelocity(Vec3d.ZERO);return;}
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
            double target=Math.toDegrees(Math.atan2(x,-z));e.setYaw((float)Motion.steer(e.getYaw(),target,e.owner==null?stats.turn():Math.max(8,stats.turn()*1.5)));
        }
        double angle=Math.toRadians(e.getYaw());
        double burst=e.has(Part.JET)&&e.age%60<12?CellAbilities.jetBoost:1;
        Vec3d desired=length>.001?new Vec3d(Math.sin(angle),0,-Math.cos(angle)).multiply(stats.speed()*e.size()*Math.min(1,length)*burst):Vec3d.ZERO;
        // Desired-velocity feedback and exponential drag, in Minecraft's 20 Hz time step.
        double response=1-Math.exp(-.05*5);
        Vec3d velocity=e.getVelocity().add(desired.subtract(e.getVelocity()).multiply(response));
        // Check every occupied cube, including off-center appendages, against obstacles.
        if(!clear(e,e.getX()+velocity.x,e.getZ()+velocity.z)) {velocity=Vec3d.ZERO;if(e.owner==null){e.setYaw(e.getYaw()+45);e.inputX=Math.sin(Math.toRadians(e.getYaw()));e.inputZ=-Math.cos(Math.toRadians(e.getYaw()));e.behavior="avoid";}}
        e.setVelocity(velocity);e.move(MovementType.SELF,velocity);e.setPosition(e.getX(),PLANE,e.getZ());
        if(e.age%4==0) {specialAttack(e);interact(e,stats);}
    }
    private static boolean clear(CellEntity e,double x,double z) {

        for(Blueprint.Piece p:e.blueprint().pieces()) {
            if(p.mounted())continue;
            Vec3d offset=offset(e,p);Box box=new Box(x+offset.x-.29*e.size(),PLANE,z+offset.z-.29*e.size(),x+offset.x+.29*e.size(),PLANE+.45*e.size(),z+offset.z+.29*e.size());
            if(!e.getWorld().isSpaceEmpty(e,box))return false;
        }
        for(CellEntity other:e.getWorld().getEntitiesByClass(CellEntity.class,e.getBoundingBox().expand(12*e.size()),o->o!=e&&!o.food()&&(!o.scenery()||o.kind()==CellEntity.ROCK)&&!o.isRemoved())) {
            for(Blueprint.Piece p:e.blueprint().pieces())for(Blueprint.Piece q:other.blueprint().pieces()) {
                if(p.mounted()||q.mounted())continue;
                Vec3d a=offset(e,p),b=offset(other,q);
                double dx=x+a.x-other.getX()-b.x,dz=z+a.z-other.getZ()-b.z;
                double oldX=e.getX()+a.x-other.getX()-b.x,oldZ=e.getZ()+a.z-other.getZ()-b.z;
                // Permit separation after a spawn or editor change creates an overlap.
                if(dx*dx+dz*dz<Math.pow(.29*e.size()+(other.kind()==CellEntity.ROCK?.8:.29)*other.size(),2) && dx*dx+dz*dz<oldX*oldX+oldZ*oldZ)return false;
            }
        }
        return true;
    }
    private static Vec3d offset(CellEntity e,Blueprint.Piece p) {
        double a=Math.toRadians(e.getYaw()),c=Math.cos(a),s=Math.sin(a);
        return new Vec3d((p.px()*c-p.pz()*s)*e.size(),p.py()*e.size(),(p.px()*s+p.pz()*c)*e.size());
    }
    private static void think(CellEntity e,Blueprint.Stats stats) {
        List<CellEntity> nearby=e.getWorld().getEntitiesByClass(CellEntity.class,e.getBoundingBox().expand(stats.sight()*e.size()),
                other->other!=e && !other.isRemoved() && !other.scenery() && (other.owner==null||!EDITING.contains(other.owner)));
        CellEntity threat=nearby.stream().filter(o->!o.food()&&(o.blueprint().stats(Parts.SPECS).carnivore()||o.has(Part.ELECTRIC))
                &&(o.level()>e.level()||e.health()<=2&&o.level()>=e.level())).min(Comparator.comparingDouble(e::squaredDistanceTo)).orElse(null);
        CellEntity target;
        if(threat!=null) { e.behavior="flee"; e.inputX=e.getX()-threat.getX();e.inputZ=e.getZ()-threat.getZ();return; }
        target=nearby.stream().filter(o->(o.kind()==CellEntity.PLANT&&stats.herbivore())||(o.kind()==CellEntity.MEAT&&stats.carnivore())
                ||(!o.food()&&stats.carnivore()&&o.level()<=e.level()))
                .min(Comparator.comparingDouble(e::squaredDistanceTo)).orElse(null);
        var food=nearby.stream().filter(o->o.kind()==CellEntity.PLANT&&stats.herbivore()||o.kind()==CellEntity.MEAT&&stats.carnivore()).min(Comparator.comparingDouble(e::squaredDistanceTo)).orElse(null);
        if(food!=null&&(e.health()<4||Math.floorMod(e.getUuid().hashCode(),3)==0))target=food;
        if(target!=null) {
            e.behavior=target.food()?"feed":"hunt";
            double dx=target.getX()-e.getX(),dz=target.getZ()-e.getZ();
            if(!target.food()&&e.has(Part.POISON)&&e.squaredDistanceTo(target)<9*e.size()*e.size()){
                e.behavior="circle";e.inputX=-dz+dx*.2;e.inputZ=dx+dz*.2;
            }else if(!target.food()&&Math.floorMod(e.getUuid().hashCode(),3)==1){
                e.behavior="ambush";e.inputX=dx+target.getVelocity().x*6;e.inputZ=dz+target.getVelocity().z*6;
            }else{e.inputX=dx;e.inputZ=dz;}
        }
        else if(e.age%60==0 || (e.inputX==0&&e.inputZ==0)) {
            e.behavior="wander";double a=e.getWorld().random.nextDouble()*Math.PI*2;
            e.inputX=Math.sin(a);e.inputZ=Math.cos(a);
        }
        for(var other:nearby)if(!other.food()&&other.kind()==e.kind()&&e.squaredDistanceTo(other)<Math.pow(2*e.size(),2)){
            e.inputX+=(e.getX()-other.getX())*.6;e.inputZ+=(e.getZ()-other.getZ())*.6;
        }
    }
    private static void specialAttack(CellEntity e){
        if(!e.has(Part.POISON)&&!e.has(Part.ELECTRIC))return;
        double radius=CellAbilities.electricRadius*e.size();
        var targets=e.getWorld().getEntitiesByClass(CellEntity.class,e.getBoundingBox().expand(radius),o->o!=e&&o.kind()<CellEntity.PLANT&&!o.isRemoved()&&(o.owner==null||!EDITING.contains(o.owner))&&e.squaredDistanceTo(o)<radius*radius);
        if(targets.isEmpty())return;
        if(e.has(Part.ELECTRIC)&&e.electricCooldown==0){
            e.effectRange(CellAbilities.electricRadius);
            e.electricTicks=8;e.electricCooldown=CellAbilities.electricCooldown;
            for(var target:targets)if(!target.has(Part.ELECTRIC)){
                hit(e,target,(float)Parts.SPECS.get(Part.ELECTRIC).damage());target.stunTicks=CellAbilities.stunDuration;
            }
        }
        if(e.has(Part.POISON)&&e.poisonCooldown==0){
            var part=e.blueprint().pieces().stream().filter(p->p.part()==Part.POISON).findFirst().orElseThrow();
            var point=e.getPos().add(offset(e,part));
            var cloud=spawn((ServerWorld)e.getWorld(),CellEntity.POISON_CLOUD,point.x,point.z,new Blueprint(List.of(new Blueprint.Piece(0,0,Part.CORE,0))));
            cloud.source=e.getUuid();cloud.level(e.level());cloud.effectRange(CellAbilities.poisonRadius);cloud.cloudTicks=CellAbilities.poisonLifetime;e.poisonCooldown=CellAbilities.poisonCooldown;
        }
    }
    private static void poisonCloud(CellEntity cloud){
        if(cloud.source==null||--cloud.cloudTicks<=0){cloud.discard();return;}
        if(cloud.age%10!=0)return;
        double radius=CellAbilities.poisonRadius*cloud.size();
        for(var target:cloud.getWorld().getEntitiesByClass(CellEntity.class,cloud.getBoundingBox().expand(radius),o->o.kind()<CellEntity.PLANT&&!o.getUuid().equals(cloud.source)&&!o.has(Part.POISON)&&!o.isRemoved()&&(o.owner==null||!EDITING.contains(o.owner))&&cloud.squaredDistanceTo(o)<radius*radius)){
            target.poisonTicks=CellAbilities.poisonDuration;target.poisonSource=cloud.source;
        }
    }
    private static void interact(CellEntity e,Blueprint.Stats stats) {
        for(CellEntity other:e.getWorld().getEntitiesByClass(CellEntity.class,e.getBoundingBox().expand(12*e.size()),o->o!=e&&!o.scenery()&&!o.isRemoved())) {
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
                if(Math.abs(Math.cos(Math.toRadians(p.pitch())))<.25 || dx*dx+dz*dz>Math.pow(.72*e.size()*p.size()+.3*other.size(),2) || !Motion.facesAngle(dx,dz,p.angle()+e.getYaw()))continue;
                if(other.food()) {
                    if((other.kind()==CellEntity.PLANT&&(p.part()==Part.HERBIVORE_MOUTH||p.part()==Part.OMNIVORE_MOUTH))
                            ||(other.kind()==CellEntity.MEAT&&(p.part()==Part.CARNIVORE_MOUTH||p.part()==Part.OMNIVORE_MOUTH))) {
                        other.discard();e.health(Math.min(6,e.health()+2));reward(e,other.kind()==CellEntity.PLANT);break;
                    }
                } else if(p.part()==Part.SPIKE || p.part()==Part.CARNIVORE_MOUTH || p.part()==Part.OMNIVORE_MOUTH) {
                    // Contact with any target cube, rather than only the nucleus.
                    int reference=e.owner!=null?e.level():other.owner!=null?other.level():e.level();
                    hit(e,other,Evolution.damage(e.owner!=null,other.owner!=null,
                            Evolution.category(e.level(),reference),Evolution.category(other.level(),reference),
                            worldDifficultyHard(e)));break;
                }
            }
        }
    }
    private static boolean worldDifficultyHard(CellEntity e){return e.getWorld().getDifficulty()==net.minecraft.world.Difficulty.HARD;}
    private static void reward(CellEntity e,boolean plant) {
        if(e.owner==null)return;
        CellSave save=CellSave.get(e.getServer());CellSave.Profile p=save.profile(e.owner);
        int before=p.progress;p.progress=Math.min(1000,p.progress+10);
        p.dna+=Evolution.earnedDna(p.progress)-Evolution.earnedDna(before);
        if(plant)p.plantFood+=10;else p.animalFood+=10;
        p.eaten++;p.health=e.health();e.level(Evolution.level(p.progress));save.markDirty();
        ServerPlayerEntity player=e.getServer().getPlayerManager().getPlayer(e.owner);
        if(player!=null)sync(player,false,p.progress==1000?"Стадия клетки пройдена! Организм готов к следующему этапу.":
                Evolution.level(before)!=e.level()?"Рост: уровень "+e.level()+". Клетка увеличилась.":"");
    }
    private static void hit(CellEntity attacker,CellEntity target,float damage) {
        if(target.hurtCooldown>0||damage<=0)return;
        target.hurtCooldown=15;target.health(target.health()-damage);
        if(attacker!=null&&attacker.owner!=null){
            TARGETS.put(attacker.owner,new CombatTarget(target.getId(),target.level(),Math.max(0,target.health()),damage,++attackSerial,attacker.getServer().getTicks()+80));
            var player=attacker.getServer().getPlayerManager().getPlayer(attacker.owner);if(player!=null)sync(player,false,"");
        }
        if(target.owner!=null){
            CellSave save=CellSave.get(target.getServer());save.profile(target.owner).health=Math.max(0,target.health());save.markDirty();
            var player=target.getServer().getPlayerManager().getPlayer(target.owner);if(player!=null&&target.health()>0)sync(player,false,"Ваша клетка получила урон.");
        }
        if(target.health()>0)return;
        if(target.owner==null) {
            CellDiscovery.fromVictim(target);
            CellEntity meat=spawn((ServerWorld)target.getWorld(),CellEntity.MEAT,target.getX(),target.getZ(),Blueprint.starter());
            meat.level(target.level());target.discard();
        } else {
            CellSave save=CellSave.get(target.getServer());CellSave.Profile p=save.profile(target.owner);
            CellReproduction.cancel(target.owner);
            p.deaths++;p.health=(float)p.blueprint.stats(Parts.SPECS).health();target.health(p.health);target.hurtCooldown=80;
            target.setPosition(0,PLANE,0);target.setVelocity(Vec3d.ZERO);save.markDirty();
            ServerPlayerEntity player=target.getServer().getPlayerManager().getPlayer(target.owner);
            if(player!=null)sync(player,false,"Клетка погибла. Возрождение в центре водоёма; конструкция и ДНК сохранены.");
        }
    }
}
