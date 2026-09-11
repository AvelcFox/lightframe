package dev.puffspark.lightframe.world;

/**
 * Read/write access to the accumulated RGB light field.
 * Keys are 16^3 sections; values are sparse byte arrays (3 bytes per voxel).
 */
public interface LightStorage {

    /** Sets the RGB value at world block coords; marks the owning section dirty. */
    void set(int x, int y, int z, int r, int g, int b);

    /** Writes {@code rgb[0..2]} at world block coords; returns false if there is no data (out of any lit volume). */
    boolean get(int x, int y, int z, int[] rgb);

    /** Max channel value (0..255) at the position, 0 if unknown. Used for the vanilla light boost. */
    int maxChannel(int x, int y, int z);

    /** True if no RGB data exists anywhere (fast global early-out). */
    boolean isEmpty();

    /** Number of sections that currently contain light. */
    int sectionCount();

    /** Removes all sections of a chunk (on chunk unload). */
    void dropChunk(int chunkX, int chunkZ);
}

