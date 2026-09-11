package dev.puffspark.lightframe.engine;

import dev.puffspark.lightframe.config.ColorLightConfig;
import dev.puffspark.lightframe.world.ChunkLightStorage;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Per-world RGB light engine: owns sources, the RGB field, the update queue
 * and the propagation. Runs on the owning side's tick thread with a time
 * budget; API mutations are enqueued from any thread.
 */
public final class RGBLightEngine {

    private final World world;
    private final ChunkLightStorage storage = new ChunkLightStorage();
    private final LightSourceRegistry sources = new LightSourceRegistry();
    private final LightUpdateQueue queue = new LightUpdateQueue();
    private final EngineListener listener;
    private final ColorLightConfig config = ColorLightConfig.get();
    public final EngineStats stats = new EngineStats();

    RGBLightEngine(World world, EngineListener listener) {
        this.world = world;
        this.listener = listener != null ? listener : EngineListener.EMPTY;
    }

    public World world() {
        return world;
    }

    public ChunkLightStorage storage() {
        return storage;
    }

    public LightSourceRegistry sources() {
        return sources;
    }

    public LightUpdateQueue queue() {
        return queue;
    }

    // ------------------------------------------------------------------ API

    /** Creates and registers a source; returns null if the source cap is hit. */
    ColorLightSource createSource(Vec3d pos, dev.puffspark.lightframe.api.LightColor color, int radius, float intensity, java.util.UUID forcedId) {
        if (sources.isFull(config.maxLightSources)) {
            return null;
        }
        ColorLightSource s = forcedId == null
                ? new ColorLightSource(world, pos, color, radius, intensity)
                : new ColorLightSource(world, pos, color, radius, intensity, forcedId);
        sources.add(s);
        enqueueUpdate(s);
        return s;
    }

    /** API mutation (color/intensity/radius/pos/enabled). */
    void sourceChanged(ColorLightSource s) {
        enqueueUpdate(s);
    }

    /** API removal. */
    void sourceRemoved(ColorLightSource s) {
        queue.enqueue(LightNode.remove(s.getId()));
    }

    private void enqueueUpdate(ColorLightSource s) {
        // state is re-read at execution; the op itself only marks "needs re-propagation"
        queue.enqueue(LightNode.update(s.getId()));
    }

    // ------------------------------------------------------------------ Tick

    /** Processes queued ops within the configured time budget. */
    public void tick() {
        double budgetMs = Math.max(0.0, config.updateBudget);
        long budgetNanos = (long) (budgetMs * 1_000_000.0);
        int before = stats.opsProcessedTotal;
        long start = System.nanoTime();

        int processed = queue.process(this::applyOp, budgetNanos);

        long elapsed = System.nanoTime() - start;
        stats.opsProcessedTotal += processed;
        stats.lastPropagationNanos = elapsed;
        stats.totalPropagationNanos += elapsed;
        stats.pendingOps = queue.size();
        stats.sources = sources.size();
        stats.storageSections = storage.sectionCount();
        if (processed > 0) {
            listener.engineTicked(stats.opsProcessedTotal - before, elapsed, stats.lastDirtySections);
        }
    }

    /** Applies one op: localized cluster re-propagation with zero cross-world rebuild churn. */
    private void applyOp(LightNode node) {
        long opStart = System.nanoTime();
        LongSet dirty = new LongOpenHashSet(64);

        int[] outBox = new int[6];
        java.util.Set<ColorLightSource> cluster = new java.util.HashSet<>();

        if (node.op == LightNode.REMOVE) {
            ColorLightSource s = sources.get(node.id);
            sources.remove(node.id);
            if (s != null) {
                int sMinX, sMinY, sMinZ, sMaxX, sMaxY, sMaxZ;
                if (s.hasBox) {
                    sMinX = s.boxMinX; sMinY = s.boxMinY; sMinZ = s.boxMinZ;
                    sMaxX = s.boxMaxX; sMaxY = s.boxMaxY; sMaxZ = s.boxMaxZ;
                } else {
                    int r = s.boxRadius();
                    BlockPos o = BlockPos.ofFloored(s.getPosition());
                    sMinX = o.getX() - r; sMinY = o.getY() - r; sMinZ = o.getZ() - r;
                    sMaxX = o.getX() + r; sMaxY = o.getY() + r; sMaxZ = o.getZ() + r;
                }
                sources.findClusterForBox(sMinX, sMinY, sMinZ, sMaxX, sMaxY, sMaxZ, cluster, outBox);
                recomputeCluster(cluster, outBox, dirty);
            }
        } else {
            ColorLightSource s = sources.get(node.id);
            if (s != null && s.isEnabled() && s.getIntensity() > 0.0f) {
                int r = s.boxRadius();
                BlockPos o = BlockPos.ofFloored(s.getPosition());
                int sMinX = o.getX() - r, sMinY = o.getY() - r, sMinZ = o.getZ() - r;
                int sMaxX = o.getX() + r, sMaxY = o.getY() + r, sMaxZ = o.getZ() + r;
                if (s.hasBox) {
                    sMinX = Math.min(sMinX, s.boxMinX);
                    sMinY = Math.min(sMinY, s.boxMinY);
                    sMinZ = Math.min(sMinZ, s.boxMinZ);
                    sMaxX = Math.max(sMaxX, s.boxMaxX);
                    sMaxY = Math.max(sMaxY, s.boxMaxY);
                    sMaxZ = Math.max(sMaxZ, s.boxMaxZ);
                }
                cluster.add(s);
                sources.findClusterForBox(sMinX, sMinY, sMinZ, sMaxX, sMaxY, sMaxZ, cluster, outBox);
                recomputeCluster(cluster, outBox, dirty);
            }
        }

        long elapsed = System.nanoTime() - opStart;
        stats.bfsRuns++;
        stats.lastPropagationNanos = elapsed;
        stats.lastDirtySections = dirty.size();
        if (!dirty.isEmpty()) {
            listener.sectionsChanged(dirty);
        }
    }

    /**
     * Called when a block state changes in the world.
     * Finds and recomputes only the local cluster of sources overlapping with the block position.
     */
    public void onBlockChanged(BlockPos pos) {
        if (sources.isEmpty()) return;

        int bx = pos.getX();
        int by = pos.getY();
        int bz = pos.getZ();

        java.util.Set<ColorLightSource> cluster = new java.util.HashSet<>();
        int[] outBox = new int[6];
        sources.findClusterForBox(bx, by, bz, bx, by, bz, cluster, outBox);

        if (cluster.isEmpty()) return;

        long opStart = System.nanoTime();
        LongSet dirty = new LongOpenHashSet(32);
        recomputeCluster(cluster, outBox, dirty);

        long elapsed = System.nanoTime() - opStart;
        stats.bfsRuns++;
        stats.lastPropagationNanos = elapsed;
        stats.lastDirtySections = dirty.size();
        if (!dirty.isEmpty()) {
            listener.sectionsChanged(dirty);
        }
    }

    /**
     * Recomputes only a local cluster of intersecting sources within their union bounding box.
     * All unrelated sources and chunks across the rest of the world remain 100% untouched.
     */
    private void recomputeCluster(java.util.Set<ColorLightSource> cluster, int[] box, LongSet dirty) {
        if (box[0] > box[3]) return;

        LightPropagation.clearBox(world, storage, dirty, box[0], box[1], box[2], box[3], box[4], box[5]);

        for (ColorLightSource s : cluster) {
            if (s.isEnabled() && s.getIntensity() > 0.0f) {
                Vec3d p = s.getPosition();
                dev.puffspark.lightframe.api.LightColor c = s.getColor();
                int r = s.boxRadius();
                LightPropagation.addSource(world, storage, dirty, p.x, p.y, p.z, c.r, c.g, c.b, s.getIntensity(), r);
                BlockPos origin = BlockPos.ofFloored(p.x, p.y, p.z);
                s.boxMinX = origin.getX() - r;
                s.boxMinY = origin.getY() - r;
                s.boxMinZ = origin.getZ() - r;
                s.boxMaxX = origin.getX() + r;
                s.boxMaxY = origin.getY() + r;
                s.boxMaxZ = origin.getZ() + r;
                s.hasBox = true;
            } else {
                s.hasBox = false;
            }
        }
        storage.invalidateCache();
    }

    /** Full recomputation of all sources (used only on full reloads or clear). */
    public void recomputeAll(LongSet dirty) {
        // Collect old lit sections and their bordering neighbors so previously lit chunks rebuild
        storage.collectLitSections(dirty);

        // Completely clear old voxel grid — eliminates all partial box clearing, compounding, and ghost lights
        storage.clearAll();

        // Propagate all active sources cleanly with commutative screen blending
        for (ColorLightSource s : sources.all()) {
            if (s.isEnabled() && s.getIntensity() > 0.0f) {
                Vec3d p = s.getPosition();
                dev.puffspark.lightframe.api.LightColor c = s.getColor();
                int r = s.boxRadius();
                LightPropagation.addSource(world, storage, dirty, p.x, p.y, p.z, c.r, c.g, c.b, s.getIntensity(), r);
                BlockPos origin = BlockPos.ofFloored(p.x, p.y, p.z);
                s.boxMinX = origin.getX() - r;
                s.boxMinY = origin.getY() - r;
                s.boxMinZ = origin.getZ() - r;
                s.boxMaxX = origin.getX() + r;
                s.boxMaxY = origin.getY() + r;
                s.boxMaxZ = origin.getZ() + r;
                s.hasBox = true;
            } else {
                s.hasBox = false;
            }
        }
        storage.invalidateCache();
    }

    // ------------------------------------------------------------ chunk lifecycle

    /** Chunk unloaded: we retain sections in memory to prevent rebuild churn when crossing borders. */
    public void onChunkUnload(int chunkX, int chunkZ) {
        // Retaining sparse sections avoids constant drop-and-repropagate cycles.
    }

    /** Chunk (re)loaded: only re-propagate if an active source never computed its box. */
    public void onChunkLoaded(int chunkX, int chunkZ) {
        for (ColorLightSource s : sources.all()) {
            if (!s.hasBox && s.isEnabled()) {
                enqueueUpdate(s);
            }
        }
    }

    public boolean hasAnyData() {
        return !storage.isEmpty();
    }

    /** Max channel (0..255) at block coords; O(1). */
    public int maxChannelAt(int x, int y, int z) {
        if (storage.isEmpty()) return 0;
        return storage.maxChannel(x, y, z);
    }

    /** Samples normalized RGB (0..1) at world coords into {@code out[0..2]}. */
    public boolean sampleTint(double wx, double wy, double wz, float[] out) {
        if (storage.isEmpty()) {
            return false;
        }
        int[] rgb = new int[3];
        boolean has = storage.get((int) Math.floor(wx), (int) Math.floor(wy), (int) Math.floor(wz), rgb);
        out[0] = rgb[0] / 255.0f;
        out[1] = rgb[1] / 255.0f;
        out[2] = rgb[2] / 255.0f;
        return has;
    }

    /** Consumer-style removal used by the API for removeAll. */
    void removeIf(Consumer<ColorLightSource> notifier, java.util.function.Predicate<ColorLightSource> predicate) {
        List<ColorLightSource> doomed = new ArrayList<>();
        for (ColorLightSource s : sources.all()) {
            if (predicate.test(s)) doomed.add(s);
        }
        for (ColorLightSource s : doomed) {
            s.alive = false;
            notifier.accept(s);
            queue.enqueue(LightNode.remove(s.getId()));
        }
    }

    public int queuedOps() {
        return queue.size();
    }
}

