package dev.puffspark.lightframe.world;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.util.math.BlockPos;

/**
 * Per-world sparse RGB light storage keyed by 16^3 section.
 * Uses a one-entry cache: mesh building and BFS both iterate with strong locality.
 */
public final class ChunkLightStorage implements LightStorage {

    private final Long2ObjectOpenHashMap<LightSection> sections = new Long2ObjectOpenHashMap<>();

    private static final class CacheEntry {
        long key = Long.MIN_VALUE;
        LightSection section = null;
    }

    private final ThreadLocal<CacheEntry> cache = ThreadLocal.withInitial(CacheEntry::new);

    public static long sectionKey(int x, int y, int z) {
        return BlockPos.asLong(x & ~15, y & ~15, z & ~15);
    }

    private LightSection section(int x, int y, int z, boolean create) {
        long key = sectionKey(x, y, z);
        CacheEntry entry = cache.get();
        if (key == entry.key) {
            if (entry.section != null || !create) return entry.section;
        }

        LightSection s;
        synchronized (sections) {
            s = sections.get(key);
            if (s == null && create) {
                s = new LightSection(key);
                sections.put(key, s);
            }
        }
        entry.key = key;
        entry.section = s;
        return s;
    }

    @Override
    public void set(int x, int y, int z, int r, int g, int b) {
        LightSection s = section(x, y, z, r > 0 || g > 0 || b > 0);
        if (s == null) return;
        s.set(x & 15, y & 15, z & 15, r, g, b);
        dev.puffspark.lightframe.engine.EngineRegistry.noteDataPresent();
    }

    @Override
    public boolean get(int x, int y, int z, int[] rgb) {
        LightSection s = section(x, y, z, false);
        if (s == null || s.isEmpty()) return false;
        s.get(x & 15, y & 15, z & 15, rgb);
        return true;
    }

    @Override
    public int maxChannel(int x, int y, int z) {
        LightSection s = section(x, y, z, false);
        if (s == null || s.isEmpty()) return 0;
        return s.maxChannelAt(x & 15, y & 15, z & 15);
    }

    /**
     * Checks whether any voxel within the Chebyshev radius {@code r} around (x,y,z) has non-zero light.
     * Uses fast section AABB culling and local voxel early-exit.
     */
    public boolean hasLightNear(int x, int y, int z, int r) {
        if (isEmpty()) return false;

        int minX = x - r, maxX = x + r;
        int minY = y - r, maxY = y + r;
        int minZ = z - r, maxZ = z + r;

        int secMinX = minX >> 4, secMaxX = maxX >> 4;
        int secMinY = minY >> 4, secMaxY = maxY >> 4;
        int secMinZ = minZ >> 4, secMaxZ = maxZ >> 4;

        for (int sy = secMinY; sy <= secMaxY; sy++) {
            for (int sz = secMinZ; sz <= secMaxZ; sz++) {
                for (int sx = secMinX; sx <= secMaxX; sx++) {
                    int secOriginX = sx << 4;
                    int secOriginY = sy << 4;
                    int secOriginZ = sz << 4;
                    LightSection s = section(secOriginX, secOriginY, secOriginZ, false);
                    if (s == null || s.isEmpty()) continue;

                    int localMinX = Math.max(0, minX - secOriginX);
                    int localMaxX = Math.min(15, maxX - secOriginX);
                    int localMinY = Math.max(0, minY - secOriginY);
                    int localMaxY = Math.min(15, maxY - secOriginY);
                    int localMinZ = Math.max(0, minZ - secOriginZ);
                    int localMaxZ = Math.min(15, maxZ - secOriginZ);

                    if (s.hasLightInLocalBox(localMinX, localMinY, localMinZ, localMaxX, localMaxY, localMaxZ)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Checks whether the given 16^3 chunk section or any of its 26 immediate neighbors
     * contains any lit voxels. Extremely fast O(1) section-level check for terrain wrapping.
     */
    public boolean hasLightNearSection(int secX, int secY, int secZ) {
        if (isEmpty()) return false;
        synchronized (sections) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        long key = BlockPos.asLong((secX + dx) << 4, (secY + dy) << 4, (secZ + dz) << 4);
                        LightSection s = sections.get(key);
                        if (s != null && !s.isEmpty()) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /** Clears a single voxel, freeing the section if it becomes empty.
     * @return previous max channel value, or -1 if there was no section with data. */
    public int clearVoxel(int x, int y, int z) {
        LightSection s = section(x, y, z, false);
        if (s == null || s.isEmpty()) return -1;
        int prev = s.maxChannelAt(x & 15, y & 15, z & 15);
        if (prev > 0) {
            s.set(x & 15, y & 15, z & 15, 0, 0, 0);
            if (s.isEmpty()) {
                removeSection(BlockPos.asLong(x & ~15, y & ~15, z & ~15));
            }
        }
        return prev;
    }

    @Override
    public boolean isEmpty() {
        synchronized (sections) {
            return sections.isEmpty();
        }
    }

    @Override
    public int sectionCount() {
        synchronized (sections) {
            return sections.size();
        }
    }

    @Override
    public void dropChunk(int chunkX, int chunkZ) {
        LongOpenHashSet doomed = null;
        synchronized (sections) {
            for (Long2ObjectOpenHashMap.Entry<LightSection> e : sections.long2ObjectEntrySet()) {
                long key = e.getLongKey();
                int bx = BlockPos.unpackLongX(key);
                int bz = BlockPos.unpackLongZ(key);
                if ((bx >> 4) == chunkX && (bz >> 4) == chunkZ) {
                    if (doomed == null) doomed = new LongOpenHashSet();
                    doomed.add(key);
                }
            }
            if (doomed != null) {
                for (long key : doomed) removeSection(key);
            }
        }
    }

    private void removeSection(long key) {
        synchronized (sections) {
            sections.remove(key);
        }
        CacheEntry entry = cache.get();
        if (key == entry.key) {
            entry.section = null;
            entry.key = Long.MIN_VALUE;
        }
    }

    public Long2ObjectOpenHashMap<LightSection> sections() {
        return sections;
    }

    public void collectLitSections(it.unimi.dsi.fastutil.longs.LongSet out) {
        synchronized (sections) {
            for (Long2ObjectOpenHashMap.Entry<LightSection> e : sections.long2ObjectEntrySet()) {
                if (!e.getValue().isEmpty()) {
                    long key = e.getLongKey();
                    out.add(key);
                    int x = BlockPos.unpackLongX(key);
                    int y = BlockPos.unpackLongY(key);
                    int z = BlockPos.unpackLongZ(key);
                    out.add(sectionKey(x - 16, y, z));
                    out.add(sectionKey(x + 16, y, z));
                    out.add(sectionKey(x, y - 16, z));
                    out.add(sectionKey(x, y + 16, z));
                    out.add(sectionKey(x, y, z - 16));
                    out.add(sectionKey(x, y, z + 16));
                }
            }
        }
    }

    public void clearAll() {
        synchronized (sections) {
            sections.clear();
        }
        invalidateCache();
    }

    public void invalidateCache() {
        CacheEntry entry = cache.get();
        entry.section = null;
        entry.key = Long.MIN_VALUE;
    }
}

