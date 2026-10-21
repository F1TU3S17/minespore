package dev.sporecell;

import dev.sporecell.core.*;
import net.minecraft.nbt.*;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import java.util.*;

public final class CellSave extends PersistentState {
    public static final Type<CellSave> TYPE = new Type<>(CellSave::new,CellSave::read,null);
    public record EvolutionEvent(int generation,int progress,int plantFood,int animalFood) {}
    public static final class Profile {
        public Blueprint blueprint=Blueprint.starter();
        public int dna=0, revision=0, deaths=0, eaten=0;
        public int progress=0,plantFood=0,animalFood=0;
        public int generation=0,lastBreedEaten=0;
        public List<EvolutionEvent> history=new ArrayList<>();
        public boolean started=false;
        public float health=Evolution.MAX_HEALTH;
        public boolean active=false;
        public String dimension="minecraft:overworld";
        public double x,y,z;
        public float yaw,pitch;
        public int gameMode;
    }
    public final Map<UUID,Profile> profiles=new HashMap<>();
    public boolean dedicatedMode;
    public int environmentVersion;
    public static CellSave get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE,"sporecell");
    }
    public Profile profile(UUID id) { return profiles.computeIfAbsent(id,k->new Profile()); }
    private static CellSave read(NbtCompound nbt,RegistryWrapper.WrapperLookup lookup) {
        CellSave save=new CellSave();
        save.dedicatedMode=nbt.getBoolean("dedicatedMode");save.environmentVersion=nbt.getInt("environmentVersion");
        NbtCompound players=nbt.getCompound("players");
        for (String id:players.getKeys()) {
            try {
                Profile p=SporeCell.GSON.fromJson(players.getString(id),Profile.class);
                p.blueprint.validate();
                if(p.history==null)p.history=new ArrayList<>();
                if (p.dna<0 || p.generation<0 || p.eaten<0 || p.lastBreedEaten<0 || p.lastBreedEaten>p.eaten || p.history.size()>64 || p.progress<0 || p.progress>1000 || p.plantFood<0 || p.animalFood<0 || !Float.isFinite(p.health)) throw new IllegalArgumentException("Invalid progress");
                save.profiles.put(UUID.fromString(id),p);
            } catch (RuntimeException e) { SporeCell.LOGGER.error("Cannot load cell profile {}",id,e); }
        }
        return save;
    }
    @Override public NbtCompound writeNbt(NbtCompound nbt,RegistryWrapper.WrapperLookup lookup) {
        NbtCompound players=new NbtCompound();
        profiles.forEach((id,p)->players.putString(id.toString(),SporeCell.GSON.toJson(p)));
        nbt.put("players",players); nbt.putInt("schema",3);nbt.putBoolean("dedicatedMode",dedicatedMode);nbt.putInt("environmentVersion",environmentVersion); return nbt;
    }
}
