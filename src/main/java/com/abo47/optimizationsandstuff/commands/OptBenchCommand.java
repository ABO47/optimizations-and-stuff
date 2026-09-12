package com.abo47.optimizationsandstuff.commands;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.abo47.optimizationsandstuff.OptimizationsPlugin;
import com.abo47.optimizationsandstuff.benchmark.BenchmarkRecorder;
import com.abo47.optimizationsandstuff.config.SettingsLoader;
import com.abo47.optimizationsandstuff.config.TuningConfig;
import com.abo47.optimizationsandstuff.logging.ModLog;

import javax.annotation.Nonnull;

/**
 * Benchmark harness command. Start begins a session, stop captures one sample
 * row, status prints live tick stats plus module counters, reload re-reads
 * settings, reset clears state. Stops are appended to the session log file.
 */
public class OptBenchCommand extends AbstractWorldCommand {

    public OptBenchCommand() {
        super("bench", "Benchmark harness: start stop status reset reload");
        addSubCommand(new Start());
        addSubCommand(new Stop());
        addSubCommand(new Status());
        addSubCommand(new Reset());
        addSubCommand(new Reload());
    }

    @Override
    protected void execute(@Nonnull final CommandContext context, @Nonnull final World world,
            @Nonnull final Store<EntityStore> store) {
        printStatus(context, world);
    }

    private static void printStatus(@Nonnull final CommandContext context, @Nonnull final World world) {
        var metric = world.getBufferedTickLengthMetricSet();
        double avg = metric.getAverage(0);
        long max = metric.calculateMax(0);
        var heap = java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        StringBuilder out = new StringBuilder(256);
        out.append("optbench ").append(world.getName())
            .append(" tps=").append(world.getTps())
            .append(" tickAvgMs=").append(avg / 1_000_000.0)
            .append(" tickMaxMs=").append(max / 1_000_000.0)
            .append(" heap=").append(heap.getUsed() / 1048576L).append('/').append(heap.getMax() / 1048576L).append("MB")
            .append(" flags=[").append(flags(world)).append(']');
        var plugin = OptimizationsPlugin.get();
        if (plugin != null) {
            out.append(' ').append(plugin.countersSnapshot());
        }
        for (var sample : BenchmarkRecorder.samples(world)) {
            out.append('\n').append(BenchmarkRecorder.formatRow(sample));
        }
        context.sendMessage(Message.raw(out.toString()));
    }

    @Nonnull
    private static String flags(@Nonnull final World world) {
        return TuningConfig.resolve(world).summarize();
    }

    private static final class Start extends AbstractWorldCommand {
        private Start() {
            super("start", "Begin a benchmark session");
        }

        @Override
        protected void execute(@Nonnull final CommandContext context, @Nonnull final World world,
                @Nonnull final Store<EntityStore> store) {
            String active = flags(world);
            BenchmarkRecorder.start(world, active);
            context.sendMessage(Message.raw("optbench session started for "
                + world.getName() + " flags=[" + active + "]"));
        }
    }

    private static final class Stop extends AbstractWorldCommand {
        private Stop() {
            super("stop", "Capture a benchmark sample row");
        }

        @Override
        protected void execute(@Nonnull final CommandContext context, @Nonnull final World world,
                @Nonnull final Store<EntityStore> store) {
            String label = "run" + (BenchmarkRecorder.samples(world).size() + 1);
            String active = flags(world);
            String counters = OptimizationsPlugin.get() != null ? OptimizationsPlugin.get().countersSnapshot() : "";
            var sample = BenchmarkRecorder.stop(world, label, active, counters);
            context.sendMessage(Message.raw(BenchmarkRecorder.formatRow(sample)
                + "\n" + BenchmarkRecorder.toJson(sample, active)));
        }
    }

    private static final class Status extends AbstractWorldCommand {
        private Status() {
            super("status", "Show live tick stats and module counters");
        }

        @Override
        protected void execute(@Nonnull final CommandContext context, @Nonnull final World world,
                @Nonnull final Store<EntityStore> store) {
            printStatus(context, world);
        }
    }

    private static final class Reset extends AbstractWorldCommand {
        private Reset() {
            super("reset", "Clear the benchmark session");
        }

        @Override
        protected void execute(@Nonnull final CommandContext context, @Nonnull final World world,
                @Nonnull final Store<EntityStore> store) {
            BenchmarkRecorder.reset(world);
            context.sendMessage(Message.raw("optbench session cleared for " + world.getName()));
        }
    }

    private static final class Reload extends AbstractWorldCommand {
        private Reload() {
            super("reload", "Reload settings files from disk");
        }

        @Override
        protected void execute(@Nonnull final CommandContext context, @Nonnull final World world,
                @Nonnull final Store<EntityStore> store) {
            SettingsLoader.reloadAll();
            ModLog.refresh();
            context.sendMessage(Message.raw("optimizations settings reloaded flags=[" + flags(world) + "]"));
        }
    }
}
