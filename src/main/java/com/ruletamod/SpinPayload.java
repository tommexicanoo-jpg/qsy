package com.ruletamod;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Paquete servidor -> cliente: "muestra la ruleta y que caiga en este sector". */
public record SpinPayload(int segmento, float desfase) implements CustomPacketPayload {

    public static final Type<SpinPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RuletaMod.MOD_ID, "girar"));

    public static final StreamCodec<ByteBuf, SpinPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SpinPayload::segmento,
            ByteBufCodecs.FLOAT, SpinPayload::desfase,
            SpinPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
