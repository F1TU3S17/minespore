package dev.sporecell;

import dev.sporecell.core.Blueprint;
import net.minecraft.nbt.*;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import java.util.*;

public final class CellSave extends PersistentState {
    public static final Type<CellSave> TYPE = new Type<>(CellSave::new,CellSave::read,null);
    public static final class Profile {
        public Blueprint blueprint=Blueprint.starter();
        public int dna=12, revision=0, deaths=0, eaten=0;
        public float health=16;
        public boolean active=false;
        public String dimension="minecraft:overworld";
        public double x,y,z;
        public float yaw,pitch;
        public int gameMode;
    }
    public final Map<UUID,Profile> profiles=new HashMap<>();
    public static CellSave get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE,"sporecell");
    }
    public Profile profile(UUID id) { return profiles.computeIfAbsent(id,k->new Profile()); }
    private static CellSave read(NbtCompound nbt,RegistryWrapper.WrapperLookup lookup) {
        CellSave save=new CellSave();
        NbtCompound players=nbt.getCompound("players");
        for (String id:players.getKeys()) {
            try {
                Profile p=SporeCell.GSON.fromJson(players.getString(id),Profile.class);
                p.blueprint.validate();
                if (p.dna<0 || !Float.isFinite(p.health)) throw new IllegalArgumentException("Invalid progress");
                save.profiles.put(UUID.fromString(id),p);
            } catch (RuntimeException e) { SporeCell.LOGGER.error("Cannot load cell profile {}",id,e); }
        }
        return save;
    }
    @Override public NbtCompound writeNbt(NbtCompound nbt,RegistryWrapper.WrapperLookup lookup) {
        NbtCompound players=new NbtCompound();
        profiles.forEach((id,p)->players.putString(id.toString(),SporeCell.GSON.toJson(p)));
        nbt.put("players",players); nbt.putInt("schema",1); return nbt;
    }
}
