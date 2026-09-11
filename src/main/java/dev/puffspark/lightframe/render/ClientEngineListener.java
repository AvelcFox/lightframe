package dev.puffspark.lightframe.render;

import dev.puffspark.lightframe.config.ColorLightConfig;
import dev.puffspark.lightframe.engine.EngineListener;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.world.ClientWorld;

import java.util.Iterator;

/**
 * Client EngineListener: accumulates dirty section keys and schedules a
 * throttled number of section rebuilds per client tick. Never schedules more
 * than {@code maxSectionsRebuiltPerTick} sections in one tick — vanilla's own
 * rebuild queue spreads the rest.
 */
public final class ClientEngineListener implements EngineListener {

    private final LongOpenHashSet pending = new LongOpenHashSet();
    private final ClientWorld world;

    public ClientEngineListener(ClientWorld world) {
        this.world = world;
    }

    @Override
    public void sectionsChanged(it.unimi.dsi.fastutil.longs.LongSet sectionKeys) {
        synchronized (this) {
            pending.addAll(sectionKeys);
        }
    }

    /** Called from END_CLIENT_TICK. */
    public void flush() {
        if (pending.isEmpty()) return;
        int budget = pending.size() > 64 ? 64 : Math.max(32, ColorLightConfig.get().maxSectionsRebuiltPerTick);
        int scheduled = 0;
        synchronized (this) {
            Iterator<Long> it = pending.iterator();
            while (it.hasNext() && scheduled < budget) {
                long key = it.next();
                VanillaLightingBackend.scheduleSectionRender(world, key);
                it.remove();
                scheduled++;
            }
        }
    }

    /** Flushes all pending sections immediately without throttling (used for block breaks/places). */
    public void flushAll() {
        if (pending.isEmpty()) return;
        synchronized (this) {
            for (long key : pending) {
                VanillaLightingBackend.scheduleSectionRender(world, key);
            }
            pending.clear();
        }
    }

    public void clear() {
        synchronized (this) {
            pending.clear();
        }
    }

    public int pendingCount() {
        synchronized (this) {
            return pending.size();
        }
    }

    /** Attached engine for the current client world (set on world change). */
    private static ClientEngineListener current;

    public static ClientEngineListener current() {
        return current;
    }

    public static void setCurrent(ClientEngineListener listener) {
        current = listener;
    }
}

