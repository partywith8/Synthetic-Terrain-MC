package io.github.partywith8.epd.server;

import io.github.partywith8.epd.Epd;
import io.github.partywith8.epd.motion.*;
import io.github.partywith8.epd.network.MotionPayload;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;

/** Owns scheduling, permissions and lifecycle. Movement math has no server dependency. */
public final class DemoController {
    private final MinecraftServer server;
    private final Random random = new Random();
    private final Map<UUID, ReturnPoint> returns = new HashMap<>();
    private final Set<UUID> debug = new HashSet<>();
    private long nextId = 1, nextAuto = 160;
    private boolean automatic = true, hold;
    private double tickMs, peakMs;
    private long sentBytesEstimate;
    private TerrainRegeneration regeneration;
    private CommandSourceStack regenerationOwner;
    private long regenerationReady, nextProgress;
    private StressScene stress;
    private UUID stressOwner;
    private boolean stressPublished;
    public record
            ReturnPoint(String dimension, double x, double y, double z, float yaw, float pitch) {
        public ReturnPoint {
            if (dimension == null || net.minecraft.resources.Identifier.tryParse(dimension) == null
                    || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || Math.abs(x) > 29_999_000 || Math.abs(z) > 29_999_000
                    || Math.abs(y) > 30_000_000 || !Float.isFinite(yaw) || !Float.isFinite(pitch))
                throw new IllegalArgumentException("Invalid return position");
        }
    }
    public DemoController(MinecraftServer server) {
        this.server = server;
    }
    public void load() {
        MotionState.server = MotionField.EMPTY;
        ReturnStore.load(server, returns);
        level().getWorldBorder().setSize(8192);
    }
    public void save() {
        ReturnStore.save(server, returns);
    }
    ServerLevel level() {
        return server.getLevel(Epd.LAB);
    }
    private long now() {
        return level().getGameTime();
    }
    static void reply(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal("STM: " + message), false);
    }
    void setDebug(UUID player, boolean enabled) {
        if (enabled)
            debug.add(player);
        else
            debug.remove(player);
    }
    void setAutomatic(boolean enabled) {
        automatic = enabled;
    }
    void setHold(boolean enabled) {
        hold = enabled;
    }
    boolean stressActive() {
        return stress != null;
    }
    int reset(CommandSourceStack source) {
        if (regeneration != null) {
            reply(source, regeneration.progress());
            return 0;
        }
        stress = null;
        stressOwner = null;
        var old = MotionState.server;
        if (old.resetStart() < 0)
            publish(new MotionField(old.revision() + 1, old.events(), now()));
        reply(source, "settling all formations over 12 seconds; mined/placed blocks stay edited");
        return 1;
    }
    int benchmark(CommandSourceStack source) {
        if (regeneration != null || stress != null || !level().players().isEmpty()) {
            reply(source, "benchmark requires an empty laboratory with no active jobs");
            return 0;
        }
        TerrainProbeCheck.benchmark(level());
        reply(source,
                "CPU probe benchmark complete; details are in the server log, not an FPS result");
        return 1;
    }
    int selftest(CommandSourceStack source) {
        if (regeneration != null || stress != null || !level().players().isEmpty()) {
            reply(source,
                    "self-test requires an empty laboratory with no regeneration or stress test");
            return 0;
        }
        try {
            RuntimeCheck.run(level());
            reply(source, "native block, collision, rider and packet self-test passed");
            return 1;
        } catch (RuntimeException error) {
            source.sendFailure(Component.literal("STM selftest: " + error.getMessage()));
            return 0;
        }
    }
    int startStress(CommandSourceStack source, int seconds)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = source.getPlayerOrException();
        if (!MotionState.applies(player.level())) {
            source.sendFailure(Component.literal("Use /stm enter first."));
            return 0;
        }
        if (stress != null || regeneration != null) {
            source.sendFailure(
                    Component.literal("A stress test or regeneration is already running."));
            return 0;
        }
        if (!ServerPlayNetworking.canSend(player, MotionPayload.TYPE)) {
            source.sendFailure(
                    Component.literal("Update Synthetic Terrain MC on your client first."));
            return 0;
        }
        var old = MotionState.server;
        long tick = now(), start = tick + 40;
        if (!old.events().isEmpty()) {
            long reset = old.resetStart() < 0 ? tick : old.resetStart();
            publish(new MotionField(old.revision() + 1, old.events(), reset));
            start = Math.max(start, reset + 240);
        }
        stress = StressScene.at(player.getBlockX(), player.getBlockZ(),
                player.requestedViewDistance(), server.getPlayerList().getViewDistance(), start,
                seconds);
        stressOwner = player.getUUID();
        stressPublished = false;
        reply(source,
                "stress queued: " + seconds + " seconds, " + stress.viewChunks()
                        + "-chunk starting view radius; coverage continues as you travel"
                        + ("; world-anchored swells up to +/-24 blocks. Existing movements settle "
                                + "first. ")
                        + ("Auto pauses; /stm stress stop eases back. /stm debug on shows "
                                + "ready/loaded meshes; wait for them to match before recording."));
        return 1;
    }
    void stopStress() {
        stress = null;
        stressOwner = null;
        var field = MotionState.server;
        if (!field.events().isEmpty() && field.resetStart() < 0)
            publish(new MotionField(field.revision() + 1, field.events(), now()));
        nextAuto = now() + 400;
    }
    private void tickStress(long tick) {
        if (stress == null)
            return;
        var owner = server.getPlayerList().getPlayer(stressOwner);
        if (owner == null || !MotionState.applies(owner.level())) {
            stopStress();
            return;
        }
        if (stress.finished(tick)) {
            owner.sendSystemMessage(Component.literal(
                    "STM: stress test complete; automatic movement resumes if enabled."));
            stress = null;
            stressOwner = null;
            nextAuto = tick + 160;
            return;
        }
        if (!stressPublished && tick >= stress.start()) {
            publish(new MotionField(
                    MotionState.server.revision() + 1, List.of(stress.event(nextId++)), -1));
            stressPublished = true;
            owner.sendSystemMessage(Component.literal("STM: stress test started; "
                    + stress.duration() / 20 + " seconds. F3+2 shows frame times."));
        }
    }
    int regenerate(CommandSourceStack source, int radius) {
        if (regeneration != null) {
            reply(source, regeneration.progress());
            return 0;
        }
        var entity = source.getEntity();
        if (entity != null && !MotionState.applies(source.getLevel())) {
            source.sendFailure(Component.literal(
                    "Use /stm enter first; regeneration only changes the laboratory."));
            return 0;
        }
        int x = entity == null ? 0 : (int) Math.floor(source.getPosition().x),
            z = entity == null ? 0 : (int) Math.floor(source.getPosition().z);
        stress = null;
        stressOwner = null;
        regeneration = new TerrainRegeneration(x >> 4, z >> 4, radius);
        regenerationOwner = source;
        var old = MotionState.server;
        if (old.events().isEmpty()) {
            publish(new MotionField(old.revision() + 1, List.of(), -1));
            regenerationReady = now();
        } else {
            long reset = old.resetStart() < 0 ? now() : old.resetStart();
            publish(new MotionField(old.revision() + 1, old.events(), reset));
            regenerationReady = reset + 240;
        }
        nextProgress = now() + 40;
        reply(source,
                "regenerating loaded chunks within chunk radius " + radius + " around " + x + ", "
                        + z
                        + ("; replacing terrain and builds with a flat white-concrete floor. "
                                + "Movements settle first; auto pauses during restoration."));
        return 1;
    }
    int enter(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = source.getPlayerOrException();
        if (!ServerPlayNetworking.canSend(player, MotionPayload.TYPE)) {
            source.sendFailure(
                    Component.literal("Install Synthetic Terrain MC on your client first."));
            return 0;
        }
        if (!MotionState.applies(player.level())) {
            returns.put(player.getUUID(),
                    new ReturnPoint(player.level().dimension().identifier().toString(),
                            player.getX(), player.getY(), player.getZ(), player.getYRot(),
                            player.getXRot()));
            save();
        }
        send(player);
        double y = 64 + MotionState.server.offset(0, 0, now());
        level().getChunk(0, 0);
        if (!player.teleportTo(level(), .5, y + 1, .5, Set.of(), 0, 0, false)) {
            source.sendFailure(Component.literal("Teleport refused"));
            return 0;
        }
        reply(source,
                "Synthetic Terrain MC. Try /trigger wave, /trigger mountain, or /stm debug on.");
        return 1;
    }
    int leave(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = source.getPlayerOrException();
        if (!MotionState.applies(player.level())) {
            reply(source, "already outside the demo");
            return 0;
        }
        var r = returns.getOrDefault(
                player.getUUID(), new ReturnPoint("minecraft:overworld", .5, 80, .5, 0, 0));
        var key = net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                net.minecraft.resources.Identifier.parse(r.dimension()));
        var destination = server.getLevel(key);
        if (destination == null)
            destination = server.overworld();
        if (!player.teleportTo(
                    destination, r.x(), r.y(), r.z(), Set.of(), r.yaw(), r.pitch(), false))
            return 0;
        reply(source, "returned to your entry position");
        return 1;
    }
    int trigger(CommandSourceStack source, Movement kind, int radius)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = source.getPlayerOrException();
        if (!MotionState.applies(player.level())) {
            source.sendFailure(Component.literal("Use /stm enter first."));
            return 0;
        }
        // Feet-centred commands make demonstration setup predictable; radius controls scope.
        if (!add(kind, (int) Math.floor(player.getX()), (int) Math.floor(player.getZ()), radius,
                    hold)) {
            source.sendFailure(
                    Component.literal("Event/overlap budget reached or reset in progress. Move "
                            + "farther away or use /stm reset."));
            return 0;
        }
        reply(source,
                kind.name().toLowerCase(Locale.ROOT) + " at "
                        + player.blockPosition().toShortString() + "; radius=" + radius + "; "
                        + (hold && kind.formation() ? "held until /stm reset" : "returns to flat"));
        return 1;
    }
    private boolean add(Movement kind, int x, int z, int radius, boolean requestedHold) {
        var old = MotionState.server;
        if (stress != null || regeneration != null || old.resetStart() >= 0
                || old.events().size() >= MotionField.MAX_EVENTS)
            return false;
        double amplitude = kind == Movement.MIRAGE ? .5
                : kind == Movement.RIPPLE          ? 3
                : kind == Movement.WAVE            ? 5
                                                   : 18;
        // Limit overlapping amplitudes so combined vertical velocity stays inside
        // the collision solver's rider-carry bound. Distant events retain all slots.
        double overlap =
                old.events()
                        .stream()
                        .filter(e -> Math.hypot(e.x() - x, e.z() - z) < e.radius() + radius)
                        .mapToDouble(MotionEvent::amplitude)
                        .sum();
        if (overlap + amplitude > 36)
            return false;
        int duration = kind == Movement.MIRAGE ? 160 : kind.formation() ? 800 : 480;
        var events = new ArrayList<>(old.events());
        events.add(new MotionEvent(nextId++, kind, x, z, radius, amplitude, now() + 30, duration,
                random.nextInt(4), requestedHold && kind.formation()));
        publish(new MotionField(old.revision() + 1, events, -1));
        return true;
    }
    private void publish(MotionField field) {
        MotionState.server = field;
        for (var p : server.getPlayerList().getPlayers())
            send(p);
    }
    public void send(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, MotionPayload.TYPE)) {
            ServerPlayNetworking.send(player, new MotionPayload(MotionState.server));
            sentBytesEstimate += 20 + MotionState.server.events().size() * 48;
        }
    }
    public String status() {
        return String.format(Locale.ROOT,
                       "events=%d/%d, auto=%s, hold=%s, reset=%s, controller=%.3f ms (peak %.3f), "
                               + "outbound~%d B",
                       MotionState.server.events().size(), MotionField.MAX_EVENTS, automatic, hold,
                       MotionState.server.resetStart() >= 0, tickMs, peakMs, sentBytesEstimate)
                + (regeneration == null ? "" : ", " + regeneration.progress())
                + (stress == null ? ""
                                  : ", stress=" + (stressPublished ? "running" : "settling")
                                        + ", view=" + stress.viewChunks() + ", remaining="
                                        + Math.max(0,
                                                (stress.start() + stress.duration() - now() + 19)
                                                        / 20)
                                        + "s");
    }
    public void tick() {
        long start = System.nanoTime();
        try {
            if (level() == null)
                return;
            long tick = now();
            var field = MotionState.server;
            if (field.resetStart() >= 0) {
                if (tick - field.resetStart() >= 240)
                    publish(new MotionField(field.revision() + 1, List.of(), -1));
            } else {
                // Most ticks expire nothing. Keep the same immutable snapshot and
                // avoid allocating a stream/list on every server tick.
                var next = field.expire(tick);
                if (next != field)
                    publish(next);
            }
            if (regeneration != null) {
                try {
                    if (tick >= regenerationReady)
                        regeneration.tick(level());
                    if (regeneration.finished()) {
                        reply(regenerationOwner, "complete: " + regeneration.progress());
                        regeneration = null;
                        regenerationOwner = null;
                        nextAuto = tick + 160;
                    } else if (tick >= nextProgress) {
                        reply(regenerationOwner,
                                (tick < regenerationReady ? "settling movements; " : "")
                                        + regeneration.progress());
                        nextProgress = tick + 40;
                    }
                } catch (RuntimeException error) {
                    reply(regenerationOwner,
                            "regeneration stopped after partial restoration: "
                                    + error.getMessage());
                    System.err.println("[STM] Regeneration failed: " + error);
                    regeneration = null;
                    regenerationOwner = null;
                }
            }
            tickStress(tick);
            if (stress == null && regeneration == null && automatic && tick >= nextAuto
                    && !level().players().isEmpty()) {
                nextAuto = tick + 120 + random.nextInt(120);
                if (MotionState.server.events().size() < 4) {
                    var p = level().players().get(random.nextInt(level().players().size()));
                    int distance = 24 + random.nextInt(72);
                    double angle = random.nextDouble() * Math.PI * 2;
                    add(Movement.AMBIENT.get(random.nextInt(Movement.AMBIENT.size())),
                            (int) p.getX() + (int) (Math.cos(angle) * distance),
                            (int) p.getZ() + (int) (Math.sin(angle) * distance),
                            32 + random.nextInt(49), false);
                }
            }
            // Teleports can remove players from the dimension's live list.
            for (var p : List.copyOf(level().players())) {
                if (p.getY() < -48)
                    p.teleportTo(level(), .5, 66 + MotionState.server.offset(0, 0, tick), .5,
                            Set.of(), p.getYRot(), 0, false);
                if (tick % 20 == 0 && debug.contains(p.getUUID())
                        && ServerPlayNetworking.canSend(
                                p, io.github.partywith8.epd.network.DebugPayload.TYPE))
                    ServerPlayNetworking.send(
                            p, new io.github.partywith8.epd.network.DebugPayload(status()));
                if (tick % 100 == 0 && !ServerPlayNetworking.canSend(p, MotionPayload.TYPE)) {
                    p.teleportTo(server.overworld(), .5, 80, .5, Set.of(), 0, 0, false);
                    p.sendSystemMessage(
                            Component.literal("STM requires the Synthetic Terrain MC client mod."));
                }
            }
            if (tick % 100 == 0)
                debug.removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        } finally {
            tickMs = (System.nanoTime() - start) / 1e6;
            peakMs = Math.max(peakMs, tickMs);
        }
    }
}
