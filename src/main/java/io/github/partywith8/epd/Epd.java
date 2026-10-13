package io.github.partywith8.epd;

import io.github.partywith8.epd.motion.MotionState;
import io.github.partywith8.epd.network.MotionPayload;
import io.github.partywith8.epd.server.DemoCommands;
import io.github.partywith8.epd.server.DemoController;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.world.level.Level;

public final class Epd implements ModInitializer {
    public static final ResourceKey<Level> LAB =
            ResourceKey.create(Registries.DIMENSION, Identifier.parse("epd:lab"));
    public static DemoController controller;
    @Override
    public void onInitialize() {
        PayloadTypeRegistry.clientboundPlay().register(MotionPayload.TYPE, MotionPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
                io.github.partywith8.epd.network.DebugPayload.TYPE,
                io.github.partywith8.epd.network.DebugPayload.CODEC);
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registry, environment) -> DemoCommands.register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            controller = new DemoController(server);
            controller.load();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (controller != null)
                controller.save();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            controller = null;
            MotionState.server = io.github.partywith8.epd.motion.MotionField.EMPTY;
        });
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            if (controller != null)
                controller.tick();
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (controller != null)
                controller.send(handler.player);
        });
    }
}
