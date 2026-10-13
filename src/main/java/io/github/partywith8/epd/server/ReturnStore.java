package io.github.partywith8.epd.server;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** Per-save entry positions. A failed write must not truncate the last good file. */
final class ReturnStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path file(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("epd-returns.json");
    }
    static void load(MinecraftServer server, Map<UUID, DemoController.ReturnPoint> out) {
        var path = file(server);
        if (!Files.isRegularFile(path))
            return;
        try {
            if (Files.size(path) > 1024 * 1024)
                throw new IllegalArgumentException("Entry file exceeds 1 MiB");
            var object = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            for (var e : object.entrySet()) {
                try {
                    var point = GSON.fromJson(e.getValue(), DemoController.ReturnPoint.class);
                    if (point != null)
                        out.put(UUID.fromString(e.getKey()), point);
                } catch (RuntimeException invalid) {
                    System.err.println("[STM] Skipping invalid return position: "
                            + invalid.getClass().getSimpleName());
                }
            }
        } catch (Exception e) {
            System.err.println("[STM] Could not load return positions: " + e);
        }
    }
    static void save(MinecraftServer server, Map<UUID, DemoController.ReturnPoint> positions) {
        var path = file(server);
        var pending = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            var object = new JsonObject();
            positions.forEach((id, p) -> object.add(id.toString(), GSON.toJsonTree(p)));
            Files.writeString(pending, GSON.toJson(object));
            Files.move(pending, path, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            System.err.println("[STM] Could not save return positions: " + e);
        }
    }
}
