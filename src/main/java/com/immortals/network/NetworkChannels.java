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

        // Items
        public record ItemS2CPayload(double x1, double y1, double z1, double x2, double y2, double z2, int rawItemId,
                        float size, int lifetimeTicks)
                        implements CustomPayload {
                public static final Identifier ITEM_PAYLOAD_ID = Identifier.of("immortals",
                                "item");
                public static final CustomPayload.Id<ItemS2CPayload> ID = new CustomPayload.Id<>(
                                ITEM_PAYLOAD_ID);
                public static final PacketCodec<RegistryByteBuf, ItemS2CPayload> CODEC = PacketCodec.tuple(
                                PacketCodecs.DOUBLE, ItemS2CPayload::x1,
                                PacketCodecs.DOUBLE, ItemS2CPayload::y1,
                                PacketCodecs.DOUBLE, ItemS2CPayload::z1,
                                PacketCodecs.DOUBLE, ItemS2CPayload::x2,
                                PacketCodecs.DOUBLE, ItemS2CPayload::y2,
                                PacketCodecs.DOUBLE, ItemS2CPayload::z2,
                                PacketCodecs.INTEGER, ItemS2CPayload::rawItemId,
                                PacketCodecs.FLOAT, ItemS2CPayload::size,
                                PacketCodecs.INTEGER, ItemS2CPayload::lifetimeTicks,
                                ItemS2CPayload::new);

                @Override
                public Id<? extends CustomPayload> getId() {
                        return ID;
                }
        }

        // Spell keybind
        public record SpellC2SPayload(int slot) implements CustomPayload {
                public static final Identifier SPELL_PAYLOAD_ID = Identifier
                                .of("immortals", "spell");
                public static final CustomPayload.Id<SpellC2SPayload> ID = new CustomPayload.Id<>(
                                SPELL_PAYLOAD_ID);
                public static final PacketCodec<RegistryByteBuf, SpellC2SPayload> CODEC = PacketCodec.tuple(
                                PacketCodecs.INTEGER, SpellC2SPayload::slot,
                                SpellC2SPayload::new);

                @Override
                public Id<? extends CustomPayload> getId() {
                        return ID;
                }
        }

        // Fragment activation
        public record FragmentC2SPayload(int dummy) implements CustomPayload {
                public static final Identifier SPELL_PAYLOAD_ID = Identifier
                                .of("immortals", "fragment");
                public static final CustomPayload.Id<FragmentC2SPayload> ID = new CustomPayload.Id<>(
                                SPELL_PAYLOAD_ID);
                public static final PacketCodec<RegistryByteBuf, FragmentC2SPayload> CODEC = PacketCodec.tuple(
                                PacketCodecs.INTEGER, FragmentC2SPayload::dummy,
                                FragmentC2SPayload::new);

                @Override
                public Id<? extends CustomPayload> getId() {
                        return ID;
                }
        }
}