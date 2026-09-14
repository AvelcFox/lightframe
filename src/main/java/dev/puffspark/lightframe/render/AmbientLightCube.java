package dev.puffspark.lightframe.render;

/**
 * 6-axis ambient lighting cube for normal-aware entity shading.
 *
 * <p>Samples RGB light arriving from each cardinal direction (+X, -X, +Y, -Y, +Z, -Z)
 * around the entity and uses quadratic cosine lobe blending to shade each model face
 * according to its surface normal:
 * <pre>
 *   C(N) = Nx^2 * C_x + Ny^2 * C_y + Nz^2 * C_z
 * </pre>
 * Because Nx^2 + Ny^2 + Nz^2 = 1.0, this yields smooth, artifact-free, 3D volumetric
 * directional lighting across complex mob and player geometry.</p>
 */
public final class AmbientLightCube {

    public final float[] posX = new float[3];
    public final float[] negX = new float[3];
    public final float[] posY = new float[3];
    public final float[] negY = new float[3];
    public final float[] posZ = new float[3];
    public final float[] negZ = new float[3];

    public final float[] center = new float[3];
    public int maxBoost = 0;

    public AmbientLightCube() {
        for (int i = 0; i < 3; i++) {
            posX[i] = 1.0f;
            negX[i] = 1.0f;
            posY[i] = 1.0f;
            negY[i] = 1.0f;
            posZ[i] = 1.0f;
            negZ[i] = 1.0f;
            center[i] = 1.0f;
        }
    }

    /**
     * Evaluates directional tint multiplier for a surface with normal (nx, ny, nz).
     * Output RGB multiplier is written into {@code out[0..2]}.
     */
    public void computeTint(float nx, float ny, float nz, float[] out) {
        float nx2 = nx * nx;
        float ny2 = ny * ny;
        float nz2 = nz * nz;
        float lenSq = nx2 + ny2 + nz2;
        if (lenSq < 1e-4f) {
            out[0] = center[0];
            out[1] = center[1];
            out[2] = center[2];
            return;
        }

        float inv = 1.0f / lenSq;
        nx2 *= inv;
        ny2 *= inv;
        nz2 *= inv;

        float[] colX = nx >= 0.0f ? posX : negX;
        float[] colY = ny >= 0.0f ? posY : negY;
        float[] colZ = nz >= 0.0f ? posZ : negZ;

        out[0] = nx2 * colX[0] + ny2 * colY[0] + nz2 * colZ[0];
        out[1] = nx2 * colX[1] + ny2 * colY[1] + nz2 * colZ[1];
        out[2] = nx2 * colX[2] + ny2 * colY[2] + nz2 * colZ[2];
    }
}
