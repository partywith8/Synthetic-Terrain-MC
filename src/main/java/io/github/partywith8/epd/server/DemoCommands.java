package io.github.partywith8.epd.server;

import static io.github.partywith8.epd.server.DemoController.reply;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.github.partywith8.epd.Epd;
import io.github.partywith8.epd.motion.Movement;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.permissions.Permissions;

/** Command syntax and permissions, separate from simulation state and ticking. */
public final class DemoCommands {
    private DemoCommands() {}
    private static DemoController live() {
        if (Epd.controller == null || Epd.controller.level() == null)
            throw new IllegalStateException("Demo world not loaded");
        return Epd.controller;
    }
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("stm").executes(c -> {
            reply(c.getSource(),
                    "/stm enter | leave | auto on/off | hold on/off | reset | regenerate "
                            + "[chunk_radius] | stress [seconds/stop] | status | debug on/off. "
                            + "/trigger "
                            + "<movement> [radius]");
            return 1;
        });
        root.then(Commands.literal("enter").executes(c -> live().enter(c.getSource())));
        root.then(Commands.literal("leave").executes(c -> live().leave(c.getSource())));
        root.then(Commands.literal("status").executes(c -> {
            reply(c.getSource(), live().status());
            return 1;
        }));
        var debug = Commands.literal("debug");
        for (boolean enabled : new boolean[] {true, false})
            debug.then(Commands.literal(enabled ? "on" : "off").executes(c -> {
                var p = c.getSource().getPlayerOrException();
                if (enabled)
                    live().setDebug(p.getUUID(), true);
                else
                    live().setDebug(p.getUUID(), false);
                reply(c.getSource(),
                        "debug "
                                + (enabled ? "enabled; action bar updates once per second"
                                           : "disabled"));
                return 1;
            }));
        root.then(debug);
        root.then(Commands.literal("stress")
                        .requires(
                                s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .executes(c -> live().startStress(c.getSource(), 60))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(15, 120))
                                        .executes(c
                                                -> live().startStress(c.getSource(),
                                                        IntegerArgumentType.getInteger(
                                                                c, "seconds"))))
                        .then(Commands.literal("stop").executes(c -> {
                            if (!live().stressActive()) {
                                reply(c.getSource(), "no stress test running");
                                return 0;
                            }
                            live().stopStress();
                            reply(c.getSource(),
                                    "stress test stopping; terrain settles over 12 seconds");
                            return 1;
                        })));
        for (String setting : List.of("auto", "hold")) {
            var branch = Commands.literal(setting).requires(
                    s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER));
            for (boolean enabled : new boolean[] {true, false})
                branch.then(Commands.literal(enabled ? "on" : "off").executes(c -> {
                    if (setting.equals("auto"))
                        live().setAutomatic(enabled);
                    else
                        live().setHold(enabled);
                    reply(c.getSource(),
                            setting + " " + (enabled ? "on" : "off")
                                    + (setting.equals("hold") ? "; applies to new formations"
                                                              : ""));
                    return 1;
                }));
            root.then(branch);
        }
        root.then(Commands.literal("reset")
                        .requires(
                                s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .executes(c -> { return live().reset(c.getSource()); }));
        root.then(Commands.literal("regenerate")
                        .requires(
                                s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .executes(c -> live().regenerate(c.getSource(), 4))
                        .then(Commands.argument("chunk_radius", IntegerArgumentType.integer(0, 8))
                                        .executes(c
                                                -> live().regenerate(c.getSource(),
                                                        IntegerArgumentType.getInteger(
                                                                c, "chunk_radius")))));
        root.then(Commands.literal("selftest")
                        .requires(
                                s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .executes(c -> { return live().selftest(c.getSource()); }));
        root.then(Commands.literal("benchmark")
                        .requires(source
                                -> source.permissions().hasPermission(
                                        Permissions.COMMANDS_GAMEMASTER))
                        .executes(context -> live().benchmark(context.getSource())));
        var registered = dispatcher.register(root);
        // Retain the existing command for saved command blocks and integrations.
        dispatcher.register(
                Commands.literal("epd").redirect(registered).executes(registered.getCommand()));
        // Literal children coexist with vanilla /trigger <scoreboard objective>.
        var trigger = Commands.literal("trigger");
        for (Movement kind : Movement.AMBIENT) {
            var command = Commands.literal(kind.name().toLowerCase(Locale.ROOT))
                                  .requires(s
                                          -> s.permissions().hasPermission(
                                                  Permissions.COMMANDS_GAMEMASTER))
                                  .executes(c -> live().trigger(c.getSource(), kind, 48));
            command.then(Commands.argument("radius", IntegerArgumentType.integer(8, 128))
                            .executes(c
                                    -> live().trigger(c.getSource(), kind,
                                            IntegerArgumentType.getInteger(c, "radius"))));
            trigger.then(command);
        }
        dispatcher.register(trigger);
    }
}
