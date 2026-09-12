package com.abo47.optimizationsandstuff.commands;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.abo47.optimizationsandstuff.OptimizationsPlugin;
import com.abo47.optimizationsandstuff.benchmark.FlagPresets;

import javax.annotation.Nonnull;

/**
 * {@code /opt test run <preset> [minutes]} — unattended preset test: applies
 * the preset, opens a bench session, shows a live toast HUD with time left and
 * auto-stops with a result row, restoring the previous flags. Default 10
 * minutes, clamped to 1-60. {@code /opt test stop} cancels early,
 * {@code /opt test status} shows the running test.
 */
public class OptTestCommand extends AbstractWorldCommand {

    public OptTestCommand() {
        super("test", "Unattended preset tests: run stop status");
        addSubCommand(new Run());
        addSubCommand(new Stop());
        addSubCommand(new Status());
    }

    @Override
    protected void execute(@Nonnull final CommandContext context, @Nonnull final World world,
            @Nonnull final Store<EntityStore> store) {
        context.sendMessage(Message.raw(statusText(world)
            + "\nPresets: " + String.join(" ", FlagPresets.names())));
    }

    @Nonnull
    private static String statusText(@Nonnull final World world) {
        var plugin = OptimizationsPlugin.get();
        if (plugin == null || plugin.getPresetTestRunner() == null) {
            return "opttest unavailable.";
        }
        return plugin.getPresetTestRunner().status(world);
    }

    private static final class Run extends AbstractWorldCommand {
        private final RequiredArg<String> presetArg =
            withRequiredArg("preset", "Flag preset to test", ArgTypes.STRING);
        private final OptionalArg<Integer> minutesArg =
            withOptionalArg("minutes", "Test duration in minutes, default 10", ArgTypes.INTEGER);

        private Run() {
            super("run", "Run a preset test with auto-stop");
        }

        @Override
        protected void execute(@Nonnull final CommandContext context, @Nonnull final World world,
                @Nonnull final Store<EntityStore> store) {
            var plugin = OptimizationsPlugin.get();
            if (plugin == null || plugin.getPresetTestRunner() == null) {
                context.sendMessage(Message.raw("opttest unavailable."));
                return;
            }
            FlagPresets.Preset preset = FlagPresets.get(presetArg.get(context));
            if (preset == null) {
                context.sendMessage(Message.raw("unknown preset. Presets: "
                    + String.join(" ", FlagPresets.names())));
                return;
            }
            int minutes = 10;
            try {
                Integer parsed = minutesArg.get(context);
                if (parsed != null) {
                    minutes = parsed;
                }
            } catch (Exception ignored) {
            }
            minutes = Math.max(1, Math.min(60, minutes));
            String error = plugin.getPresetTestRunner().begin(world, preset, minutes);
            context.sendMessage(Message.raw(error != null ? error
                : "opttest '" + preset.name + "' running for " + minutes + "m."));
        }
    }

    private static final class Stop extends AbstractWorldCommand {
        private Stop() {
            super("stop", "Cancel the running test early");
        }

        @Override
        protected void execute(@Nonnull final CommandContext context, @Nonnull final World world,
                @Nonnull final Store<EntityStore> store) {
            var plugin = OptimizationsPlugin.get();
            if (plugin == null || plugin.getPresetTestRunner() == null) {
                context.sendMessage(Message.raw("opttest unavailable."));
                return;
            }
            String row = plugin.getPresetTestRunner().cancel(world);
            context.sendMessage(Message.raw(row != null ? row : "opttest: no test running."));
        }
    }

    private static final class Status extends AbstractWorldCommand {
        private Status() {
            super("status", "Show the running test and time left");
        }

        @Override
        protected void execute(@Nonnull final CommandContext context, @Nonnull final World world,
                @Nonnull final Store<EntityStore> store) {
            context.sendMessage(Message.raw(statusText(world)));
        }
    }
}
