package dev.puffspark.lightframe.engine;

/** Simple cumulative counters for the debug HUD. */
public final class EngineStats {

    public volatile long lastPropagationNanos;
    public volatile long totalPropagationNanos;
    public volatile int opsProcessedTotal;
    public volatile int bfsRuns;
    public volatile int lastDirtySections;
    public volatile int pendingOps;
    public volatile int sources;
    public volatile int storageSections;
}

