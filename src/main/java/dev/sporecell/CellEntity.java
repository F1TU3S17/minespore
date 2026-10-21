package dev.sporecell;

import dev.sporecell.core.*;
import net.minecraft.entity.*;
import net.minecraft.entity.data.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;
import java.util.UUID;

public final class CellEntity extends Entity {
    public static final int PLAYER=0, GRAZER=1, HUNTER=2, PLANT=3, MEAT=4, ROCK=5, BACKGROUND=6, MATE=7, EGG=8, POISON_CLOUD=9, METEOR=10, PART_PICKUP=11;
    private static final TrackedData<String> LOOT=DataTracker.registerData(CellEntity.class,TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Integer> EFFECTS=DataTracker.registerData(CellEntity.class,TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Float> EFFECT_RANGE=DataTracker.registerData(CellEntity.class,TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<String> BODY=DataTracker.registerData(CellEntity.class,TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Integer> KIND=DataTracker.registerData(CellEntity.class,TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Float> HEALTH=DataTracker.registerData(CellEntity.class,TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Integer> LEVEL=DataTracker.registerData(CellEntity.class,TrackedDataHandlerRegistry.INTEGER);
    public UUID owner;
    public int lastHurtAge=-1000;
    public float lastDamage,previousClientHealth=6;
    public double inputX,inputZ;
    public int inputAge=100, hurtCooldown;
    public int poisonTicks,stunTicks,poisonCooldown,electricCooldown,cloudTicks=80,electricTicks;
    public UUID source,poisonSource;
    public String behavior="wander";
    private String cachedJson="";
    private Blueprint cachedBlueprint=Blueprint.starter();
    public CellEntity(EntityType<? extends CellEntity> type,World world) { super(type,world); setNoGravity(true); }
    @Override protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(BODY,SporeCell.GSON.toJson(Blueprint.starter())); builder.add(KIND,0); builder.add(HEALTH,6f);builder.add(LEVEL,1);builder.add(EFFECTS,0);builder.add(EFFECT_RANGE,0f);builder.add(LOOT,"");
    }
    public Blueprint blueprint() {
        String value=dataTracker.get(BODY);
        if (!value.equals(cachedJson)) { cachedBlueprint=SporeCell.GSON.fromJson(value,Blueprint.class); cachedJson=value; }
        return cachedBlueprint;
    }
    public void blueprint(Blueprint b) { b.validate(); dataTracker.set(BODY,SporeCell.GSON.toJson(b)); }
    public int kind() { return dataTracker.get(KIND); }
    public boolean discovery(){return kind()==METEOR||kind()==PART_PICKUP;}
    public Part loot(){try{return Part.valueOf(dataTracker.get(LOOT));}catch(IllegalArgumentException ex){return null;}}
    public void loot(Part part){dataTracker.set(LOOT,part==null?"":part.name());}
    public int effects(){return dataTracker.get(EFFECTS);}
    public void effects(int value){dataTracker.set(EFFECTS,value);}
    public float effectRange(){return dataTracker.get(EFFECT_RANGE);}
    public void effectRange(double value){dataTracker.set(EFFECT_RANGE,(float)value);}
    public boolean has(Part part){return blueprint().pieces().stream().anyMatch(p->p.part()==part);}
    @Override public boolean shouldRender(double squaredDistance){return squaredDistance<Math.pow(128*Math.max(1,size()),2);}
    public void kind(int k) { dataTracker.set(KIND,k); }
    public float health() { return dataTracker.get(HEALTH); }
    public int level() { return dataTracker.get(LEVEL); }
    public void level(int value) { dataTracker.set(LEVEL,Math.clamp(value,0,10)); }
    public void health(float h) { dataTracker.set(HEALTH,h); }
    @Override public void onTrackedDataSet(TrackedData<?> data){
        super.onTrackedDataSet(data);
        if(data.equals(HEALTH)&&getWorld()!=null&&getWorld().isClient){
            float value=health();if(value<previousClientHealth){lastHurtAge=age;lastDamage=previousClientHealth-value;}
            previousClientHealth=value;
        }
    }
    public boolean food() { return kind()==PLANT||kind()==MEAT; }
    public boolean scenery() {return kind()>=ROCK;}
    public double size() {return CellScale.size(level());}
    public double radius() {return blueprint().pieces().stream().mapToDouble(p->(Math.hypot(p.px(),p.pz())+p.size())/.65).max().orElse(1)*.65*size();}
    @Override public net.minecraft.util.math.Box getVisibilityBoundingBox() {return getBoundingBox().expand(radius());}
    @Override public void tick() {
        super.tick();
        if (!getWorld().isClient) CellGame.tick(this);
    }
    @Override protected void readCustomDataFromNbt(NbtCompound nbt) {
        try { blueprint(SporeCell.GSON.fromJson(nbt.getString("body"),Blueprint.class)); }
        catch (RuntimeException e) { blueprint(Blueprint.starter()); }
        kind(nbt.getInt("kind")); health(nbt.getFloat("health"));
        level(nbt.contains("level")?nbt.getInt("level"):1);
        owner=nbt.containsUuid("owner")?nbt.getUuid("owner"):null;
        dataTracker.set(LOOT,nbt.getString("loot"));
    }
    @Override protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putString("body",dataTracker.get(BODY)); nbt.putInt("kind",kind()); nbt.putFloat("health",health());
        nbt.putInt("level",level());
        if(owner!=null) nbt.putUuid("owner",owner);
        nbt.putString("loot",dataTracker.get(LOOT));
    }
    @Override public boolean canHit() { return false; }
    @Override public boolean isPushable() { return false; }
}
