package dev.sporecell;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class CellPackets {
    private CellPackets() {}
    public record Action(String json) implements CustomPayload {
        public static final Id<Action> ID=new Id<>(Identifier.of("sporecell","action"));
        public static final PacketCodec<RegistryByteBuf,Action> CODEC=PacketCodec.tuple(PacketCodecs.string(65536),Action::json,Action::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record State(String json) implements CustomPayload {
        public static final Id<State> ID=new Id<>(Identifier.of("sporecell","state"));
        public static final PacketCodec<RegistryByteBuf,State> CODEC=PacketCodec.tuple(PacketCodecs.string(65536),State::json,State::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
}
