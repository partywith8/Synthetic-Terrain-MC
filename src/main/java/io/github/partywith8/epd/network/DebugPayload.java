package io.github.partywith8.epd.network;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Private, opt-in diagnostic text. Never forwards console or filesystem data. */
public record DebugPayload(String message) implements CustomPacketPayload {
    public static final Type<DebugPayload> TYPE = new Type<>(Identifier.parse("epd:debug_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DebugPayload> CODEC =
            new StreamCodec<>() {
                public DebugPayload decode(RegistryFriendlyByteBuf b) {
                    return new DebugPayload(b.readUtf(1024));
                }
                public void encode(RegistryFriendlyByteBuf b, DebugPayload p) {
                    b.writeUtf(p.message(), 1024);
                }
            };
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
