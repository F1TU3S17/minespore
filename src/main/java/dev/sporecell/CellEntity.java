package dev.sporecell;

import dev.sporecell.core.*;
import net.minecraft.entity.*;
import net.minecraft.entity.data.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;
import java.util.UUID;

public final class CellEntity extends Entity {
    public static final int PLAYER=0, GRAZER=1, HUNTER=2, PLANT=3, MEAT=4, ROCK=5, BACKGROUND=6;
    private static final TrackedData<String> BODY=DataTracker.registerData(CellEntity.class,TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Integer> KIND=DataTracker.registerData(CellEntity.class,TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Float> HEALTH=DataTracker.registerData(CellEntity.class,TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Integer> LEVEL=DataTracker.registerData(CellEntity.class,TrackedDataHandlerRegistry.INTEGER);
    public UUID owner;
    public double inputX,inputZ;
    public int inputAge=100, hurtCooldown;
    public String behavior="wander";
    private String cachedJson="";
    private Blueprint cachedBlueprint=Blueprint.starter();
    public CellEntity(EntityType<? extends CellEntity> type,World world) { super(type,world); setNoGravity(true); }
    @Override protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(BODY,SporeCell.GSON.toJson(Blueprint.starter())); builder.add(KIND,0); builder.add(HEALTH,6f);builder.add(LEVEL,1);
    }
    public Blueprint blueprint() {
        String value=dataTracker.get(BODY);
        if (!value.equals(cachedJson)) { cachedBlueprint=SporeCell.GSON.fromJson(value,Blueprint.class); cachedJson=value; }
        return cachedBlueprint;
    }
    public void blueprint(Blueprint b) { b.validate(); dataTracker.set(BODY,SporeCell.GSON.toJson(b)); }
    public int kind() { return dataTracker.get(KIND); }
    public void kind(int k) { dataTracker.set(KIND,k); }
    public float health() { return dataTracker.get(HEALTH); }
    public int level() { return dataTracker.get(LEVEL); }
    public void level(int value) { dataTracker.set(LEVEL,Math.clamp(value,0,10)); }
    public void health(float h) { dataTracker.set(HEALTH,h); }
    public boolean food() { return kind()==PLANT||kind()==MEAT; }
    public boolean scenery() {return kind()==ROCK||kind()==BACKGROUND;}
    public double size() {return CellScale.size(level());}
    public double radius() {return blueprint().pieces().stream().mapToDouble(p->Math.hypot(p.x(),p.z())+.8).max().orElse(1)*.65*size();}
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
    }
    @Override protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putString("body",dataTracker.get(BODY)); nbt.putInt("kind",kind()); nbt.putFloat("health",health());
        nbt.putInt("level",level());
        if(owner!=null) nbt.putUuid("owner",owner);
    }
    @Override public boolean canHit() { return false; }
    @Override public boolean isPushable() { return false; }
}
