package com.abo47.optimizationsandstuff.benchmark;

import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.commands.world.perf.WorldPerfCommand;
import com.abo47.optimizationsandstuff.config.ConfigPaths;
import com.abo47.optimizationsandstuff.logging.ModLog;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Command-thread benchmark recorder. Captures one {@link Sample} per run from
 * the world's own tick metric set, JVM GC beans and heap usage. Every session
 * appends a readable entry to its log file under logs/ so results survive
 * restarts. All state is keyed by world name; methods must be called from
 * command context.
 */
public final class BenchmarkRecorder {

    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter READABLE_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final class Session {
        long startNanos;
        final Map<String, long[]> gcStart = new ConcurrentHashMap<>();
        final List<Sample> samples = new ArrayList<>();
        @Nullable Path logFile;
    }

    public static final class Sample {
        public final String label;
        public final double tpsAvg;
        public final double tpsMin;
        public final double tickAvgMs;
        public final double tickMinMs;
        public final double tickMaxMs;
        public final long gcCountDelta;
        public final long gcMsDelta;
        public final String gcDetail;
        public final long heapUsedBytes;
        public final long heapMaxBytes;
        public final int playerCount;
        public final long entityCount;
        public final long chunkEntryCount;

        private Sample(String label, double tpsAvg, double tpsMin, double tickAvgMs, double tickMinMs,
                double tickMaxMs, long gcCountDelta, long gcMsDelta, String gcDetail,
                long heapUsedBytes, long heapMaxBytes, int playerCount, long entityCount, long chunkEntryCount) {
            this.label = label;
            this.tpsAvg = tpsAvg;
            this.tpsMin = tpsMin;
            this.tickAvgMs = tickAvgMs;
            this.tickMinMs = tickMinMs;
            this.tickMaxMs = tickMaxMs;
            this.gcCountDelta = gcCountDelta;
            this.gcMsDelta = gcMsDelta;
            this.gcDetail = gcDetail;
            this.heapUsedBytes = heapUsedBytes;
            this.heapMaxBytes = heapMaxBytes;
            this.playerCount = playerCount;
            this.entityCount = entityCount;
            this.chunkEntryCount = chunkEntryCount;
        }
    }

    private static final Map<String, Session> SESSIONS = new ConcurrentHashMap<>();

    private BenchmarkRecorder() {
    }

    public static void start(@Nonnull final World world, @Nonnull final String flags) {
        world.clearMetrics();
        Session session = new Session();
        session.startNanos = System.nanoTime();
        snapshotGcInto(session.gcStart);
        session.logFile = openSessionLog(world.getName(), flags);
        SESSIONS.put(world.getName(), session);
    }

    @Nonnull
    public static Sample stop(@Nonnull final World world, @Nonnull final String rawLabel,
            @Nonnull final String flags, @Nonnull final String counters) {
        String label = ConfigPaths.sanitizeFileName(rawLabel);
        var metric = world.getBufferedTickLengthMetricSet();
        long step = world.getTickStepNanos();
        double avg = metric.getAverage(0);
        long min = metric.calculateMin(0);
        long max = metric.calculateMax(0);
        Session session = SESSIONS.get(world.getName());
        GcTotals gc = session != null ? diffGcSince(session.gcStart) : new GcTotals(0, 0, "no session");
        var heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        int players = world.getPlayerRefs().size();
        long[] loaded = world.isInThread() ? countLoaded(world) : new long[]{-1, -1};
        Sample sample = new Sample(label,
            WorldPerfCommand.tpsFromDelta(avg, step),
            WorldPerfCommand.tpsFromDelta((double) max, step),
            avg / 1_000_000.0,
            min / 1_000_000.0,
            max / 1_000_000.0,
            gc.count,
            gc.timeMs,
            gc.detail,
            heap.getUsed(),
            heap.getMax(),
            players,
            loaded[0],
            loaded[1]);
        if (session != null) {
            session.samples.add(sample);
            appendLine(session.logFile, formatEntry(world.getName(), sample, flags, counters));
        }
        return sample;
    }

    @Nonnull
    public static List<Sample> samples(@Nonnull final World world) {
        Session session = SESSIONS.get(world.getName());
        if (session == null) {
            return List.of();
        }
        return List.copyOf(session.samples);
    }

    public static void reset(@Nonnull final World world) {
        Session session = SESSIONS.remove(world.getName());
        if (session != null) {
            appendLine(session.logFile, "Session closed at " + now() + System.lineSeparator());
        }
        world.clearMetrics();
    }

    @Nonnull
    public static String formatRow(@Nonnull final Sample sample) {
        return "bench[" + sample.label + "] tps avg=" + sample.tpsAvg + " min=" + sample.tpsMin
            + " tickMs avg=" + round3(sample.tickAvgMs) + " min=" + round3(sample.tickMinMs)
            + " max=" + round3(sample.tickMaxMs) + " gc=" + sample.gcDetail
            + " heap=" + sample.heapUsedBytes + "/" + sample.heapMaxBytes
            + " players=" + sample.playerCount
            + " entities=" + sample.entityCount + " chunkEntries=" + sample.chunkEntryCount;
    }

    @Nonnull
    public static String toJson(@Nonnull final Sample sample, @Nonnull final String flags) {
        return "{\"label\":\"" + sample.label + "\",\"tpsAvg\":" + sample.tpsAvg
            + ",\"tpsMin\":" + sample.tpsMin + ",\"tickAvgMs\":" + round3(sample.tickAvgMs)
            + ",\"tickMinMs\":" + round3(sample.tickMinMs) + ",\"tickMaxMs\":" + round3(sample.tickMaxMs)
            + ",\"gcCount\":" + sample.gcCountDelta + ",\"gcMs\":" + sample.gcMsDelta
            + ",\"heap\":" + sample.heapUsedBytes + ",\"heapMax\":" + sample.heapMaxBytes
            + ",\"players\":" + sample.playerCount
            + ",\"entities\":" + sample.entityCount + ",\"chunkEntries\":" + sample.chunkEntryCount
            + ",\"flags\":\"" + flags + "\"}";
    }

    @Nonnull
    static String formatEntry(@Nonnull final String worldName, @Nonnull final Sample sample,
            @Nonnull final String flags, @Nonnull final String counters) {
        StringBuilder out = new StringBuilder(512);
        out.append("--------------------------------------------------------------------------------").append(System.lineSeparator());
        out.append("Run:        ").append(sample.label).append(System.lineSeparator());
        out.append("World:      ").append(worldName).append(System.lineSeparator());
        out.append("Stopped:    ").append(now()).append(System.lineSeparator());
        out.append("Flags:      ").append(flags).append(System.lineSeparator());
        out.append("TPS:        avg ").append(round2(sample.tpsAvg)).append(", min ").append(round2(sample.tpsMin)).append(System.lineSeparator());
        out.append("Tick:       avg ").append(round3(sample.tickAvgMs)).append(" ms"
            + ", min ").append(round3(sample.tickMinMs)).append(" ms"
            + ", max ").append(round3(sample.tickMaxMs)).append(" ms").append(System.lineSeparator());
        out.append("GC:         ").append(sample.gcDetail).append(System.lineSeparator());
        out.append("Heap:       ").append(sample.heapUsedBytes).append(" bytes (")
            .append(round1(sample.heapUsedBytes / 1048576.0)).append(" MB of ")
            .append(round1(sample.heapMaxBytes / 1048576.0)).append(" MB max)").append(System.lineSeparator());
        out.append("Players:    ").append(sample.playerCount).append(System.lineSeparator());
        out.append("Loaded:     ").append(sample.entityCount).append(" entities, ")
            .append(sample.chunkEntryCount).append(" chunk store entries").append(System.lineSeparator());
        out.append("Modules:    ").append(counters).append(System.lineSeparator());
        return out.toString();
    }

    @Nullable
    private static Path openSessionLog(@Nonnull final String worldName, @Nonnull final String flags) {
        try {
            Path dir = ConfigPaths.logsDirectory();
            Files.createDirectories(dir);
            String fileName = "bench-" + ConfigPaths.sanitizeFileName(worldName)
                + "-" + LocalDateTime.now().format(FILE_STAMP) + ".log";
            Path logFile = dir.resolve(fileName);
            StringBuilder header = new StringBuilder(256);
            header.append("================================================================================").append(System.lineSeparator());
            header.append("Benchmark session for world '").append(worldName).append("'").append(System.lineSeparator());
            header.append("Started:    ").append(now()).append(System.lineSeparator());
            header.append("Flags:      ").append(flags).append(System.lineSeparator());
            header.append("Compare runs in this file: earlier stop above, later stop below.").append(System.lineSeparator());
            Files.writeString(logFile, header.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            return logFile;
        } catch (IOException e) {
            ModLog.warning("Failed to open benchmark log: " + e.getMessage());
            return null;
        }
    }

    private static void appendLine(@Nullable final Path logFile, @Nonnull final String entry) {
        if (logFile == null) {
            return;
        }
        try {
            Files.writeString(logFile, entry, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } catch (IOException e) {
            ModLog.warning("Failed to append benchmark log: " + e.getMessage());
        }
    }

    @Nonnull
    private static String now() {
        return LocalDateTime.now().format(READABLE_STAMP);
    }

    private static final class GcTotals {
        long count;
        long timeMs;
        String detail;

        GcTotals(final long count, final long timeMs, final String detail) {
            this.count = count;
            this.timeMs = timeMs;
            this.detail = detail;
        }
    }

    private static void snapshotGcInto(@Nonnull final Map<String, long[]> target) {
        target.clear();
        for (var bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            target.put(bean.getName(), new long[]{Math.max(0, bean.getCollectionCount()), Math.max(0, bean.getCollectionTime())});
        }
    }

    @Nonnull
    private static GcTotals diffGcSince(@Nonnull final Map<String, long[]> start) {
        long count = 0;
        long timeMs = 0;
        StringBuilder detail = new StringBuilder();
        for (var bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long[] before = start.get(bean.getName());
            long countDelta = Math.max(0, bean.getCollectionCount()) - (before != null ? before[0] : 0);
            long timeDelta = Math.max(0, bean.getCollectionTime()) - (before != null ? before[1] : 0);
            if (countDelta <= 0 && timeDelta <= 0) {
                continue;
            }
            count += countDelta;
            timeMs += timeDelta;
            if (detail.length() > 0) {
                detail.append("; ");
            }
            detail.append(bean.getName()).append(' ').append(countDelta).append("x/").append(timeDelta).append("ms");
        }
        if (detail.length() == 0) {
            detail.append("no collections");
        }
        return new GcTotals(count, timeMs, detail.toString());
    }

    /**
     * Counts loaded entities and chunk store entries. Must run on the world
     * thread, which is where world commands execute.
     */
    @Nonnull
    private static long[] countLoaded(@Nonnull final World world) {
        long[] entities = new long[1];
        world.getEntityStore().getStore().forEachChunk((archetypeChunk, commandBuffer) -> {
            entities[0] += archetypeChunk.size();
        });
        long[] chunks = new long[1];
        world.getChunkStore().getStore().forEachChunk((archetypeChunk, commandBuffer) -> {
            chunks[0] += archetypeChunk.size();
        });
        return new long[]{entities[0], chunks[0]};
    }

    private static double round1(final double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static double round2(final double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static double round3(final double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
