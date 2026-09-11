package dev.puffspark.lightframe.engine;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of live sources for one world.
 */
public final class LightSourceRegistry {

    private final Map<UUID, ColorLightSource> sources = new ConcurrentHashMap<>();

    public void add(ColorLightSource s) {
        sources.put(s.getId(), s);
    }

    public ColorLightSource get(UUID id) {
        return sources.get(id);
    }

    public boolean remove(UUID id) {
        return sources.remove(id) != null;
    }

    public Collection<ColorLightSource> all() {
        return sources.values();
    }

    public int size() {
        return sources.size();
    }

    public boolean isEmpty() {
        return sources.isEmpty();
    }

    public boolean isFull(int max) {
        return sources.size() >= max;
    }

    /** Sources whose last-propagated box intersects the given box. */
    public void intersecting(int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                             java.util.List<ColorLightSource> out) {
        for (ColorLightSource s : sources.values()) {
            if (!s.hasBox) continue;
            if (s.boxMaxX < minX || s.boxMinX > maxX) continue;
            if (s.boxMaxY < minY || s.boxMinY > maxY) continue;
            if (s.boxMaxZ < minZ || s.boxMinZ > maxZ) continue;
            out.add(s);
        }
    }

    /**
     * Finds the connected component of all sources that transitively overlap with the given initial box.
     * Computes the combined union bounding box in outBox[0..5] (minX, minY, minZ, maxX, maxY, maxZ).
     */
    public void findClusterForBox(int bMinX, int bMinY, int bMinZ, int bMaxX, int bMaxY, int bMaxZ,
                                 java.util.Set<ColorLightSource> outCluster, int[] outBox) {
        outBox[0] = bMinX; outBox[1] = bMinY; outBox[2] = bMinZ;
        outBox[3] = bMaxX; outBox[4] = bMaxY; outBox[5] = bMaxZ;

        boolean expanded = true;
        while (expanded) {
            expanded = false;
            for (ColorLightSource s : sources.values()) {
                if (!s.isEnabled() || s.getIntensity() <= 0.0f) continue;
                if (outCluster.contains(s)) continue;

                int sMinX, sMinY, sMinZ, sMaxX, sMaxY, sMaxZ;
                if (s.hasBox) {
                    sMinX = s.boxMinX; sMinY = s.boxMinY; sMinZ = s.boxMinZ;
                    sMaxX = s.boxMaxX; sMaxY = s.boxMaxY; sMaxZ = s.boxMaxZ;
                } else {
                    int r = s.boxRadius();
                    net.minecraft.util.math.BlockPos o = net.minecraft.util.math.BlockPos.ofFloored(s.getPosition());
                    sMinX = o.getX() - r; sMinY = o.getY() - r; sMinZ = o.getZ() - r;
                    sMaxX = o.getX() + r; sMaxY = o.getY() + r; sMaxZ = o.getZ() + r;
                }

                // Check overlap with current cluster union box
                if (sMaxX >= outBox[0] && sMinX <= outBox[3] &&
                    sMaxY >= outBox[1] && sMinY <= outBox[4] &&
                    sMaxZ >= outBox[2] && sMinZ <= outBox[5]) {
                    outCluster.add(s);
                    outBox[0] = Math.min(outBox[0], sMinX);
                    outBox[1] = Math.min(outBox[1], sMinY);
                    outBox[2] = Math.min(outBox[2], sMinZ);
                    outBox[3] = Math.max(outBox[3], sMaxX);
                    outBox[4] = Math.max(outBox[4], sMaxY);
                    outBox[5] = Math.max(outBox[5], sMaxZ);
                    expanded = true;
                }
            }
        }
    }

    public Optional<ColorLightSource> byId(UUID id) {
        return Optional.ofNullable(sources.get(id));
    }
}

