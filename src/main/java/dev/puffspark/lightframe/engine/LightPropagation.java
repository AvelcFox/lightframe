package dev.puffspark.lightframe.engine;

import dev.puffspark.lightframe.world.ChunkLightStorage;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Spherical Euclidean BFS propagation of a single source into shared RGB storage.
 * Uses true Euclidean distance falloff to guarantee circular/spherical light pools
 * without diamond/Manhattan distortion. Uses commutative screen blending for
 * realistic multi-light color mixing.
 */
public final class LightPropagation {

    private LightPropagation() {}

    private static final int[][] DIRS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    private static final ThreadLocal<Long2IntOpenHashMap> SOURCE_SCRATCH =
            ThreadLocal.withInitial(() -> new Long2IntOpenHashMap(2048));
    private static final ThreadLocal<LongArrayList> TOUCHED_SCRATCH =
            ThreadLocal.withInitial(() -> new LongArrayList(2048));
    private static final ThreadLocal<LongArrayFIFOQueue> QUEUE_SCRATCH =
            ThreadLocal.withInitial(() -> new LongArrayFIFOQueue(1024));
    private static final ThreadLocal<int[]> CUR_SCRATCH =
            ThreadLocal.withInitial(() -> new int[3]);

    /**
     * Writes the source's light field into storage with additive/screen blending,
     * marking touched section keys in {@code dirty}.
     */
    public static void addSource(World world, ChunkLightStorage storage, LongSet dirty,
                                 double px, double py, double pz,
                                 float cr, float cg, float cb,
                                 float intensity, int radius) {
        BlockPos origin = BlockPos.ofFloored(px, py, pz);
        int sx = origin.getX();
        int sy = origin.getY();
        int sz = origin.getZ();

        int er = emission(cr, intensity);
        int eg = emission(cg, intensity);
        int eb = emission(cb, intensity);
        if (er <= 0 && eg <= 0 && eb <= 0) return;

        int minX = (int) Math.floor(px - radius), minY = Math.max(world.getBottomY(), (int) Math.floor(py - radius)), minZ = (int) Math.floor(pz - radius);
        int maxX = (int) Math.ceil(px + radius), maxY = Math.min(world.getTopY() - 1, (int) Math.ceil(py + radius)), maxZ = (int) Math.ceil(pz + radius);

        Long2IntOpenHashMap sourceLight = SOURCE_SCRATCH.get();
        sourceLight.clear();
        sourceLight.defaultReturnValue(0);

        LongArrayList touched = TOUCHED_SCRATCH.get();
        touched.clear();

        LongArrayFIFOQueue queue = QUEUE_SCRATCH.get();
        queue.clear();

        BlockPos.Mutable mpos = new BlockPos.Mutable();

        int packedOrigin = (er << 16) | (eg << 8) | eb;
        long originKey = BlockPos.asLong(sx, sy, sz);
        sourceLight.put(originKey, packedOrigin);
        touched.add(originKey);
        queue.enqueue(originKey);

        double radiusDouble = radius;
        double radiusSq = radiusDouble * radiusDouble;

        while (!queue.isEmpty()) {
            long packed = queue.dequeueLong();
            int x = BlockPos.unpackLongX(packed);
            int y = BlockPos.unpackLongY(packed);
            int z = BlockPos.unpackLongZ(packed);

            for (int dir = 0; dir < 6; dir++) {
                int nx = x + DIRS[dir][0];
                int ny = y + DIRS[dir][1];
                int nz = z + DIRS[dir][2];
                if (nx < minX || nx > maxX || ny < minY || ny > maxY || nz < minZ || nz > maxZ) continue;

                long nKey = BlockPos.asLong(nx, ny, nz);
                if (sourceLight.containsKey(nKey)) continue; // Early exit before expensive getBlockState!

                int opacity = opacityOf(world, mpos, nx, ny, nz);
                if (opacity >= 15) continue; // fully opaque: stops light

                // True Euclidean distance from actual source coordinates to voxel center:
                double dx = (nx + 0.5) - px;
                double dy = (ny + 0.5) - py;
                double dz = (nz + 0.5) - pz;
                double distSq = dx * dx + dy * dy + dz * dz;
                if (distSq > radiusSq) continue;

                double dist = Math.sqrt(distSq);
                float t = (float) (dist / radiusDouble);
                if (t >= 1.0f) continue;

                // Smooth Hermite falloff curve: 1 - t*t*(3 - 2*t) gives a bright luminous core and soft outer fade
                float falloff = Math.max(0.0f, 1.0f - t * t * (3.0f - 2.0f * t));

                // Partial opacity attenuation (e.g. water, leaves)
                if (opacity > 0) {
                    falloff *= Math.max(0.0f, 1.0f - (opacity / 15.0f));
                }

                int nr = Math.min(255, (int) Math.round(er * falloff));
                int ng = Math.min(255, (int) Math.round(eg * falloff));
                int nbb = Math.min(255, (int) Math.round(eb * falloff));
                if (nr == 0 && ng == 0 && nbb == 0) continue;

                sourceLight.put(nKey, (nr << 16) | (ng << 8) | nbb);
                touched.add(nKey);
                queue.enqueue(nKey);
            }
        }

        int[] cur = CUR_SCRATCH.get();
        // Screen blending: C_blend = C1 + C2 - (C1 * C2) / 255
        int count = touched.size();
        for (int i = 0; i < count; i++) {
            long key = touched.getLong(i);
            int val = sourceLight.get(key);
            int sr = (val >> 16) & 0xFF;
            int sg = (val >> 8) & 0xFF;
            int sb = val & 0xFF;

            int vx = BlockPos.unpackLongX(key);
            int vy = BlockPos.unpackLongY(key);
            int vz = BlockPos.unpackLongZ(key);

            if (!storage.get(vx, vy, vz, cur)) {
                cur[0] = 0; cur[1] = 0; cur[2] = 0;
            }

            int newR = cur[0] + sr - (cur[0] * sr) / 255;
            int newG = cur[1] + sg - (cur[1] * sg) / 255;
            int newB = cur[2] + sb - (cur[2] * sb) / 255;

            storage.set(vx, vy, vz, newR, newG, newB);
            dirty.add(ChunkLightStorage.sectionKey(vx, vy, vz));
        }
    }

    /**
     * Zeroes all RGB values inside the box and frees emptied sections.
     * Used before re-propagation on remove/move.
     */
    public static void clearBox(World world, ChunkLightStorage storage, LongSet dirty,
                                int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        minY = Math.max(minY, world.getBottomY());
        maxY = Math.min(maxY, world.getTopY() - 1);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    if (storage.clearVoxel(x, y, z) > 0) {
                        dirty.add(ChunkLightStorage.sectionKey(x, y, z));
                    }
                }
            }
        }
        storage.invalidateCache();
    }

    private static int emission(float channel, float intensity) {
        float v = Math.min(1.0f, Math.max(0.0f, channel * intensity));
        return (int) (v * 255.0f);
    }

    private static int opacityOf(World world, BlockPos.Mutable mpos, int x, int y, int z) {
        mpos.set(x, y, z);
        BlockState state = world.getBlockState(mpos);
        return state.getOpacity(world, mpos);
    }
}

