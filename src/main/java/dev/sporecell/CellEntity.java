package dev.sporecell;

import dev.sporecell.core.*;
import net.minecraft.entity.*;
import net.minecraft.entity.data.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;
import java.util.UUID;

public final class CellEntity extends Entity {
    public static final int PLAYER=0, GRAZER=1, HUNTER=2, PLANT=3, MEAT=4;
    private static final TrackedData<String> BODY=DataTracker.registerData(CellEntity.class,TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Integer> KIND=DataTracker.registerData(CellEntity.class,TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Float> HEALTH=DataTracker.registerData(CellEntity.class,TrackedDataHandlerRegistry.FLOAT);
    public UUID owner;
    public double inputX,inputZ;
    public int inputAge=100, hurtCooldown;
    public String behavior="wander";
    private String cachedJson="";
    private Blueprint cachedBlueprint=Blueprint.starter();
    public CellEntity(EntityType<? extends CellEntity> type,World world) { super(type,world); setNoGravity(true); }
    @Override protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(BODY,SporeCell.GSON.toJson(Blueprint.starter())); builder.add(KIND,0); builder.add(HEALTH,16f);
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
    public void health(float h) { dataTracker.set(HEALTH,h); }
    public boolean food() { return kind()>=PLANT; }
    @Override public void tick() {
        super.tick();
        if (!getWorld().isClient) CellGame.tick(this);
    }
    @Override protected void readCustomDataFromNbt(NbtCompound nbt) {
        try { blueprint(SporeCell.GSON.fromJson(nbt.getString("body"),Blueprint.class)); }
        catch (RuntimeException e) { blueprint(Blueprint.starter()); }
        kind(nbt.getInt("kind")); health(nbt.getFloat("health"));
        owner=nbt.containsUuid("owner")?nbt.getUuid("owner"):null;
    }
    @Override protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putString("body",dataTracker.get(BODY)); nbt.putInt("kind",kind()); nbt.putFloat("health",health());
        if(owner!=null) nbt.putUuid("owner",owner);
    }
    @Override public boolean canHit() { return false; }
    @Override public boolean isPushable() { return false; }
}
