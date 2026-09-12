package com.abo47.optimizationsandstuff.commands;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Single entry point: /opt bench|test|flag.
 */
public class OptCommand extends AbstractWorldCommand {

    public OptCommand() {
        super("opt", "Optimizations: bench test flag");
        addSubCommand(new OptBenchCommand());
        addSubCommand(new OptTestCommand());
        addSubCommand(new OptFlagCommand());
    }

    @Override
    protected void execute(@Nonnull final CommandContext context, @Nonnull final World world,
            @Nonnull final Store<EntityStore> store) {
        context.sendMessage(Message.raw("usage: /opt bench ... | /opt test ... | /opt flag ..."));
    }
}
