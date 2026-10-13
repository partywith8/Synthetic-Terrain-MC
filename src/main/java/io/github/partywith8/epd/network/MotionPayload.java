package io.github.partywith8.epd.network;

import io.github.partywith8.epd.motion.*;
import java.util.ArrayList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** At most eight events. The constructor validates all untrusted numeric fields. */
public record MotionPayload(MotionField field) implements CustomPacketPayload {
    // v3 keeps stress active beyond its initial view. Older clients use a bounded
    // field, so accepting them would disagree with server collision after travel.
    public static final Type<MotionPayload> TYPE = new Type<>(Identifier.parse("epd:motion_v3"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MotionPayload> CODEC =
            new StreamCodec<>() {
                public MotionPayload decode(RegistryFriendlyByteBuf b) {
                    long revision = b.readLong(), reset = b.readLong();
                    int count = b.readVarInt();
                    if (count < 0 || count > MotionField.MAX_EVENTS)
                        throw new IllegalArgumentException("Event limit");
                    var events = new ArrayList<MotionEvent>(count);
                    for (int i = 0; i < count; i++) {
                        long id = b.readLong();
                        int kind = b.readVarInt();
                        if (kind < 0 || kind >= Movement.values().length)
                            throw new IllegalArgumentException("Movement ID");
                        events.add(new MotionEvent(id, Movement.values()[kind], b.readInt(),
                                b.readInt(), b.readVarInt(), b.readDouble(), b.readLong(),
                                b.readVarInt(), b.readVarInt(), b.readBoolean()));
                    }
                    return new MotionPayload(new MotionField(revision, events, reset));
                }
                public void encode(RegistryFriendlyByteBuf b, MotionPayload payload) {
                    var f = payload.field;
                    b.writeLong(f.revision());
                    b.writeLong(f.resetStart());
                    b.writeVarInt(f.events().size());
                    for (var e : f.events()) {
                        b.writeLong(e.id());
                        b.writeVarInt(e.kind().ordinal());
                        b.writeInt(e.x());
                        b.writeInt(e.z());
                        b.writeVarInt(e.radius());
                        b.writeDouble(e.amplitude());
                        b.writeLong(e.start());
                        b.writeVarInt(e.duration());
                        b.writeVarInt(e.axis());
                        b.writeBoolean(e.hold());
                    }
                }
            };
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
