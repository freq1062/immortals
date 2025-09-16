package com.immortals.network;

import net.minecraft.util.Identifier;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

// common class
public final class NetworkChannels {
        // Runes
        public record RuneS2CPayload(String spellId, double x, double y, double z, float maxSize, int lifetimeTicks)
                        implements CustomPayload {
                public static final Identifier RUNE_PAYLOAD_ID = Identifier.of("immortals",
                                "rune");
                public static final CustomPayload.Id<RuneS2CPayload> ID = new CustomPayload.Id<>(
                                RUNE_PAYLOAD_ID);

                public static final PacketCodec<RegistryByteBuf, RuneS2CPayload> CODEC = PacketCodec.tuple(
                                PacketCodecs.STRING, RuneS2CPayload::spellId,
                                PacketCodecs.DOUBLE, RuneS2CPayload::x,
                                PacketCodecs.DOUBLE, RuneS2CPayload::y,
                                PacketCodecs.DOUBLE, RuneS2CPayload::z,
                                PacketCodecs.FLOAT, RuneS2CPayload::maxSize,
                                PacketCodecs.INTEGER, RuneS2CPayload::lifetimeTicks,
                                RuneS2CPayload::new);

                @Override
                public Id<? extends CustomPayload> getId() {
                        return ID;
                }
        }

        // Spheres
        public record SphereS2CPayload(float r, float g, float b, float a, double x, double y, double z, float maxSize,
                        int lifetimeTicks)
                        implements CustomPayload {
                public static final Identifier SPHERE_PAYLOAD_ID = Identifier.of("immortals",
                                "sphere");
                public static final CustomPayload.Id<SphereS2CPayload> ID = new CustomPayload.Id<>(
                                SPHERE_PAYLOAD_ID);
                public static final PacketCodec<RegistryByteBuf, SphereS2CPayload> CODEC = PacketCodec.tuple(
                                PacketCodecs.FLOAT, SphereS2CPayload::r,
                                PacketCodecs.FLOAT, SphereS2CPayload::g,
                                PacketCodecs.FLOAT, SphereS2CPayload::b,
                                PacketCodecs.FLOAT, SphereS2CPayload::a,
                                PacketCodecs.DOUBLE, SphereS2CPayload::x,
                                PacketCodecs.DOUBLE, SphereS2CPayload::y,
                                PacketCodecs.DOUBLE, SphereS2CPayload::z,
                                PacketCodecs.FLOAT, SphereS2CPayload::maxSize,
                                PacketCodecs.INTEGER, SphereS2CPayload::lifetimeTicks,
                                SphereS2CPayload::new);

                @Override
                public Id<? extends CustomPayload> getId() {
                        return ID;
                }
        }
}