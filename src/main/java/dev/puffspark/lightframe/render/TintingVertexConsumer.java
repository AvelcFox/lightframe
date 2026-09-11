package dev.puffspark.lightframe.render;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Transparent decorator over {@link VertexConsumer} that applies RGB lighting tint
 * and boosts vanilla block light in the vertex lightmap coordinates.
 * Supports smooth per-vertex gradient tinting for chunk terrain meshes and
 * uniform tinting for entities / fluids.
 */
public final class TintingVertexConsumer implements VertexConsumer {

    private final VertexConsumer delegate;
    private final World world;
    private final BlockPos blockPos;
    private final float uniformTr, uniformTg, uniformTb;
    private final int fallbackBoost;
    private final boolean perVertex;

    /** Constructor for terrain blocks with per-vertex smooth gradient tinting and light boost. */
    public TintingVertexConsumer(VertexConsumer delegate, World world, BlockPos blockPos,
                                 float fallbackR, float fallbackG, float fallbackB, int fallbackBoost) {
        this.delegate = delegate;
        this.world = world;
        this.blockPos = blockPos;
        this.uniformTr = fallbackR;
        this.uniformTg = fallbackG;
        this.uniformTb = fallbackB;
        this.fallbackBoost = fallbackBoost;
        this.perVertex = true;
    }

    /** Constructor for entities and fallback flat tinting with optional light boost. */
    public TintingVertexConsumer(VertexConsumer delegate, float r, float g, float b, int boost) {
        this.delegate = delegate;
        this.world = null;
        this.blockPos = null;
        this.uniformTr = r;
        this.uniformTg = g;
        this.uniformTb = b;
        this.fallbackBoost = boost;
        this.perVertex = false;
    }

    /** Constructor for entities and fallback flat tinting. */
    public TintingVertexConsumer(VertexConsumer delegate, float r, float g, float b) {
        this(delegate, r, g, b, 0);
    }

    public VertexConsumer delegate() {
        return delegate;
    }

    // ---------------------------------------------------------- tinted entry points

    @Override
    public void quad(MatrixStack.Entry entry, BakedQuad quad, float red, float green, float blue, float alpha,
                     int light, int overlay) {
        if (!perVertex || world == null || blockPos == null) {
            delegate.quad(entry, quad, red * uniformTr, green * uniformTg, blue * uniformTb, alpha, light, overlay);
            return;
        }

        int[] vertexData = quad.getVertexData();
        Direction face = quad.getFace();
        Matrix4f posMat = entry.getPositionMatrix();
        Matrix3f normMat = entry.getNormalMatrix();
        Vec3i faceVec = face.getVector();
        Vector3f normal = new Vector3f((float) faceVec.getX(), (float) faceVec.getY(), (float) faceVec.getZ());
        normal.mul(normMat);

        float[] vTint = new float[3];

        for (int k = 0; k < 4; k++) {
            int off = k * 8;
            float vx = Float.intBitsToFloat(vertexData[off + 0]);
            float vy = Float.intBitsToFloat(vertexData[off + 1]);
            float vz = Float.intBitsToFloat(vertexData[off + 2]);

            int boost = VanillaLightingBackend.sampleVertexTintAndBoost(world, blockPos, face, vx, vy, vz, vTint);

            int colorInt = vertexData[off + 3];
            float l = (float) (colorInt & 0xFF) / 255.0f;
            float m = (float) ((colorInt >>> 8) & 0xFF) / 255.0f;
            float n = (float) ((colorInt >>> 16) & 0xFF) / 255.0f;
            float a = (float) ((colorInt >>> 24) & 0xFF) / 255.0f;

            float r = l * red * vTint[0];
            float g = m * green * vTint[1];
            float b = n * blue * vTint[2];

            float u = Float.intBitsToFloat(vertexData[off + 4]);
            float v = Float.intBitsToFloat(vertexData[off + 5]);

            int blockLight = light & 0xFFFF;
            int skyLight = (light >> 16) & 0xFFFF;
            int finalBlockLight = Math.max(blockLight, boost);
            int finalLight = (skyLight << 16) | finalBlockLight;

            Vector4f worldPos = posMat.transform(new Vector4f(vx, vy, vz, 1.0f));
            delegate.vertex(worldPos.x(), worldPos.y(), worldPos.z())
                    .color(r, g, b, alpha * a)
                    .texture(u, v)
                    .overlay(overlay)
                    .light(finalLight)
                    .normal(normal.x(), normal.y(), normal.z());
        }
    }

    @Override
    public void quad(MatrixStack.Entry entry, BakedQuad quad, float[] brightness,
                     float red, float green, float blue, float alpha,
                     int[] lights, int overlay, boolean useWorldLight) {
        if (!perVertex || world == null || blockPos == null) {
            delegate.quad(entry, quad, brightness, red * uniformTr, green * uniformTg, blue * uniformTb, alpha, lights, overlay, useWorldLight);
            return;
        }

        int[] vertexData = quad.getVertexData();
        Direction face = quad.getFace();
        Matrix4f posMat = entry.getPositionMatrix();
        Matrix3f normMat = entry.getNormalMatrix();
        Vec3i faceVec = face.getVector();
        Vector3f normal = new Vector3f((float) faceVec.getX(), (float) faceVec.getY(), (float) faceVec.getZ());
        normal.mul(normMat);

        float[] vTint = new float[3];

        for (int k = 0; k < 4; k++) {
            int off = k * 8;
            float vx = Float.intBitsToFloat(vertexData[off + 0]);
            float vy = Float.intBitsToFloat(vertexData[off + 1]);
            float vz = Float.intBitsToFloat(vertexData[off + 2]);

            int boost = VanillaLightingBackend.sampleVertexTintAndBoost(world, blockPos, face, vx, vy, vz, vTint);

            int colorInt = vertexData[off + 3];
            float l = (float) (colorInt & 0xFF) / 255.0f;
            float m = (float) ((colorInt >>> 8) & 0xFF) / 255.0f;
            float n = (float) ((colorInt >>> 16) & 0xFF) / 255.0f;
            float a = (float) ((colorInt >>> 24) & 0xFF) / 255.0f;

            float r = (useWorldLight ? l * red : red) * brightness[k] * vTint[0];
            float g = (useWorldLight ? m * green : green) * brightness[k] * vTint[1];
            float b = (useWorldLight ? n * blue : blue) * brightness[k] * vTint[2];

            float u = Float.intBitsToFloat(vertexData[off + 4]);
            float v = Float.intBitsToFloat(vertexData[off + 5]);

            int vertexLight = lights[k];
            int blockLight = vertexLight & 0xFFFF;
            int skyLight = (vertexLight >> 16) & 0xFFFF;
            int finalBlockLight = Math.max(blockLight, boost);
            int finalLight = (skyLight << 16) | finalBlockLight;

            Vector4f worldPos = posMat.transform(new Vector4f(vx, vy, vz, 1.0f));
            delegate.vertex(worldPos.x(), worldPos.y(), worldPos.z())
                    .color(r, g, b, alpha * a)
                    .texture(u, v)
                    .overlay(overlay)
                    .light(finalLight)
                    .normal(normal.x(), normal.y(), normal.z());
        }
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        return delegate.color(
                (int) (red * uniformTr),
                (int) (green * uniformTg),
                (int) (blue * uniformTb),
                alpha);
    }

    @Override
    public VertexConsumer color(float red, float green, float blue, float alpha) {
        return delegate.color(red * uniformTr, green * uniformTg, blue * uniformTb, alpha);
    }

    // ---------------------------------------------------------- pass-through

    @Override
    public VertexConsumer vertex(float x, float y, float z) {
        return delegate.vertex(x, y, z);
    }

    @Override
    public VertexConsumer vertex(Matrix4f matrix, float x, float y, float z) {
        return delegate.vertex(matrix, x, y, z);
    }

    @Override
    public VertexConsumer color(int argb) {
        return delegate.color(argb);
    }

    @Override
    public VertexConsumer texture(float u, float v) {
        return delegate.texture(u, v);
    }

    @Override
    public VertexConsumer overlay(int uv) {
        return delegate.overlay(uv);
    }

    @Override
    public VertexConsumer overlay(int u, int v) {
        return delegate.overlay(u, v);
    }

    @Override
    public VertexConsumer light(int uv) {
        if (fallbackBoost > 0) {
            int blockLight = uv & 0xFFFF;
            int skyLight = (uv >> 16) & 0xFFFF;
            uv = (skyLight << 16) | Math.max(blockLight, fallbackBoost);
        }
        return delegate.light(uv);
    }

    @Override
    public VertexConsumer light(int u, int v) {
        if (fallbackBoost > 0) {
            u = Math.max(u, fallbackBoost);
        }
        return delegate.light(u, v);
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        return delegate.normal(x, y, z);
    }

    @Override
    public VertexConsumer normal(MatrixStack.Entry entry, float x, float y, float z) {
        return delegate.normal(entry, x, y, z);
    }
}
