package dev.puffspark.lightframe.world;

/**
 * A 16^3 section of accumulated RGB light.
 * Data is a flat byte[4096 * 3] (R,G,B per voxel), allocated lazily.
 * Emptiness is tracked with a counter (O(1) check), so bulk clears stay cheap.
 * Section key = BlockPos.asLong(section origin in block coords).
 */
public final class LightSection {

    public static final int EDGE = 16;
    public static final int VOL = 4096;

    private final long key; // BlockPos.asLong of the section origin
    private byte[] data;    // 4096 * 3
    private int minLitX = 16, minLitY = 16, minLitZ = 16;
    private int maxLitX = -1, maxLitY = -1, maxLitZ = -1;
    private int nonEmpty;   // voxels with at least one channel > 0

    public LightSection(long key) {
        this.key = key;
    }

    public long key() {
        return key;
    }

    public boolean isEmpty() {
        return nonEmpty == 0;
    }

    private static int idx(int lx, int ly, int lz) {
        return (lz << 8) | (ly << 4) | lx;
    }

    public void set(int lx, int ly, int lz, int r, int g, int b) {
        if (data == null) data = new byte[VOL * 3];
        int i = idx(lx, ly, lz) * 3;
        int or = data[i] & 0xFF;
        int og = data[i + 1] & 0xFF;
        int ob = data[i + 2] & 0xFF;
        boolean wasEmpty = or == 0 && og == 0 && ob == 0;
        data[i] = (byte) r;
        data[i + 1] = (byte) g;
        data[i + 2] = (byte) b;
        boolean nowEmpty = r == 0 && g == 0 && b == 0;
        if (wasEmpty && !nowEmpty) {
            nonEmpty++;
            if (lx < minLitX) minLitX = lx;
            if (lx > maxLitX) maxLitX = lx;
            if (ly < minLitY) minLitY = ly;
            if (ly > maxLitY) maxLitY = ly;
            if (lz < minLitZ) minLitZ = lz;
            if (lz > maxLitZ) maxLitZ = lz;
        } else if (!wasEmpty && nowEmpty) {
            nonEmpty--;
            if (nonEmpty == 0) {
                resetBounds();
            }
        } else if (!nowEmpty) {
            if (lx < minLitX) minLitX = lx;
            if (lx > maxLitX) maxLitX = lx;
            if (ly < minLitY) minLitY = ly;
            if (ly > maxLitY) maxLitY = ly;
            if (lz < minLitZ) minLitZ = lz;
            if (lz > maxLitZ) maxLitZ = lz;
        }
    }

    private void resetBounds() {
        minLitX = 16; minLitY = 16; minLitZ = 16;
        maxLitX = -1; maxLitY = -1; maxLitZ = -1;
    }

    public void get(int lx, int ly, int lz, int[] out3) {
        if (data == null) {
            out3[0] = out3[1] = out3[2] = 0;
            return;
        }
        int i = idx(lx, ly, lz) * 3;
        out3[0] = data[i] & 0xFF;
        out3[1] = data[i + 1] & 0xFF;
        out3[2] = data[i + 2] & 0xFF;
    }

    public int maxChannelAt(int lx, int ly, int lz) {
        if (data == null) return 0;
        int i = idx(lx, ly, lz) * 3;
        int r = data[i] & 0xFF;
        int g = data[i + 1] & 0xFF;
        int b = data[i + 2] & 0xFF;
        return Math.max(r, Math.max(g, b));
    }

    public boolean isEmptyAfterClear(int lx, int ly, int lz) {
        if (data == null) return true;
        int i = idx(lx, ly, lz) * 3;
        boolean wasEmpty = (data[i] & 0xFF) == 0 && (data[i + 1] & 0xFF) == 0 && (data[i + 2] & 0xFF) == 0;
        if (!wasEmpty) {
            data[i] = 0;
            data[i + 1] = 0;
            data[i + 2] = 0;
            nonEmpty--;
            if (nonEmpty == 0) {
                resetBounds();
                return true;
            }
        }
        return false;
    }

    public boolean hasLightInLocalBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (nonEmpty == 0 || data == null) return false;
        if (maxX < minLitX || minX > maxLitX ||
            maxY < minLitY || minY > maxLitY ||
            maxZ < minLitZ || minZ > maxLitZ) {
            return false;
        }
        int cMinX = Math.max(minX, minLitX);
        int cMaxX = Math.min(maxX, maxLitX);
        int cMinY = Math.max(minY, minLitY);
        int cMaxY = Math.min(maxY, maxLitY);
        int cMinZ = Math.max(minZ, minLitZ);
        int cMaxZ = Math.min(maxZ, maxLitZ);

        for (int z = cMinZ; z <= cMaxZ; z++) {
            for (int y = cMinY; y <= cMaxY; y++) {
                for (int x = cMinX; x <= cMaxX; x++) {
                    int i = idx(x, y, z) * 3;
                    if (data[i] != 0 || data[i + 1] != 0 || data[i + 2] != 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void clear() {
        data = null;
        nonEmpty = 0;
        resetBounds();
    }

    /** Only for iteration/diagnostics. */
    public byte[] raw() {
        return data;
    }
}

