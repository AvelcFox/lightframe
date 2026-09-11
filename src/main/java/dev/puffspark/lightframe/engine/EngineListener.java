package dev.puffspark.lightframe.engine;

import it.unimi.dsi.fastutil.longs.LongSet;

/**
 * Callback from the engine to the platform glue.
 * On the client this schedules chunk section rebuilds; on the server it is a no-op counter.
 */
public interface EngineListener {

    EngineListener EMPTY = new EngineListener() {
        @Override
        public void sectionsChanged(LongSet sectionKeys) {}
    };

    /**
     * Called after a propagation step produced changed sections.
     * The implementation must throttle rebuilds (budget) itself.
     */
    void sectionsChanged(LongSet sectionKeys);

    /** Diagnostic: engine processed ops / ran BFS. */
    default void engineTicked(int opsProcessed, long propagationNanos, int dirtySections) {}
}

