package com.abo47.optimizationsandstuff.commands;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.abo47.optimizationsandstuff.OptimizationsPlugin;
import com.abo47.optimizationsandstuff.benchmark.FlagPresets;
import com.abo47.optimizationsandstuff.config.ModSettings;
import com.abo47.optimizationsandstuff.config.SettingsLoader;
import com.abo47.optimizationsandstuff.config.TuningConfig;

import javax.annotation.Nonnull;

/**
 * {@code /opt flag <name> <on|off>} — toggle one flag at runtime with no
 * restart: the change is written to settings.json and takes effect on the next
 * tick. {@code /opt flag list} shows the toggleable flags. Refused while a test
 * runs in the world so the test restore stays exact.
 */
public class OptFlagCommand extends AbstractWorldCommand {

    private final RequiredArg<String> nameArg =
        withRequiredArg("name", "Flag name, see /opt flag list", ArgTypes.STRING);
    private final RequiredArg<String> stateArg =
        withRequiredArg("state", "on or off", ArgTypes.STRING);

    public OptFlagCommand() {
        super("flag", "Toggle one flag: /opt flag <name> <on|off>, /opt flag list");
        addSubCommand(new List());
    }

    @Override
    protected void execute(@Nonnull final CommandContext context, @Nonnull final World world,
            @Nonnull final Store<EntityStore> store) {
        var plugin = OptimizationsPlugin.get();
        if (plugin == null) {
            context.sendMessage(Message.raw("optflag unavailable."));
            return;
        }
        if (plugin.getPresetTestRunner() != null && plugin.getPresetTestRunner().hasTest(world)) {
            context.sendMessage(Message.raw("a test is running here — use /opt test stop first."));
            return;
        }
        String state = stateArg.get(context);
        boolean on;
        if (state.equalsIgnoreCase("on") || state.equalsIgnoreCase("true")) {
            on = true;
        } else if (state.equalsIgnoreCase("off") || state.equalsIgnoreCase("false")) {
            on = false;
        } else {
            context.sendMessage(Message.raw("state must be on or off."));
            return;
        }
        ModSettings next = SettingsLoader.snapshot();
        String error = FlagPresets.setFlag(next, nameArg.get(context), on);
        if (error != null) {
            context.sendMessage(Message.raw(error));
            return;
        }
        SettingsLoader.save(next);
        context.sendMessage(Message.raw("flags=[" + TuningConfig.resolve(world).summarize() + "]"));
    }

    private static final class List extends AbstractWorldCommand {
        private List() {
            super("list", "Show toggleable flags");
        }

        @Override
        protected void execute(@Nonnull final CommandContext context, @Nonnull final World world,
                @Nonnull final Store<EntityStore> store) {
            context.sendMessage(Message.raw("Toggleable: " + String.join(" ", FlagPresets.flags())
                + " (usage: /opt flag <name> <on|off>)"));
        }
    }
}
