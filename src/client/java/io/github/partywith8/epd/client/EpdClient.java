package io.github.partywith8.epd.client;

import io.github.partywith8.epd.motion.*;
import io.github.partywith8.epd.network.MotionPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.Minecraft;

public final class EpdClient implements ClientModInitializer {
    public static volatile boolean active;
    @Override
    public void onInitializeClient() {
        MotionState.partialTick =
                () -> Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        ClientPlayNetworking.registerGlobalReceiver(
                io.github.partywith8.epd.network.DebugPayload.TYPE,
                (packet, context) -> context.client().execute(() -> {
                    context.client().gui.hud.setOverlayMessage(
                            net.minecraft.network.chat.Component.literal("STM " + packet.message()
                                    + " | " + TerrainRenderer.diagnostics()),
                            false);
                }));
        ClientPlayNetworking.registerGlobalReceiver(
                MotionPayload.TYPE, (packet, context) -> context.client().execute(() -> {
                    if (packet.field().revision() >= MotionState.client.revision())
                        MotionState.client = packet.field();
                }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            MotionState.client = MotionField.EMPTY;
            active = false;
            TerrainRenderer.close();
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean next = MotionState.applies(client.level);
            if (next != active) {
                TerrainRenderer.close();
                active = next;
            }
            TerrainRenderer.tick();
        });
        net.fabricmc.fabric.api.resource.ResourceManagerHelper
                .get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES)
                .registerReloadListener(new net.fabricmc.fabric.api.resource
                                .SimpleSynchronousResourceReloadListener() {
                                    public net.minecraft.resources.Identifier getFabricId() {
                                        return net.minecraft.resources.Identifier.parse(
                                                "epd:terrain");
                                    }
                                    public void onResourceManagerReload(net.minecraft.server.packs
                                                    .resources.ResourceManager resources) {
                                        Minecraft.getInstance().execute(TerrainRenderer::close);
                                    }
                                });
    }
}
