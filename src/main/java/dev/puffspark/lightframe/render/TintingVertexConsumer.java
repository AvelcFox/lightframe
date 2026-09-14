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
    private final AmbientLightCube ambientCube;

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
        this.ambientCube = null;
    }

    /** Constructor for entities and fallback flat tinting with optional light boost. */
    public TintingVertexConsumer(VertexConsumer delegate, float r, float g, float b, int boost) {
        this(delegate, r, g, b, boost, null);
    }

    /** Constructor for entities with normal-aware directional lighting cube. */
    public TintingVertexConsumer(VertexConsumer delegate, float r, float g, float b, int boost, AmbientLightCube ambientCube) {
        this.delegate = delegate;
        this.world = null;
        this.blockPos = null;
        this.uniformTr = r;
        this.uniformTg = g;
        this.uniformTb = b;
        this.fallbackBoost = boost;
        this.perVertex = false;
        this.ambientCube = ambientCube;
    }

    /** Constructor for entities and fallback flat tinting. */
    public TintingVertexConsumer(VertexConsumer delegate, float r, float g, float b) {
        this(delegate, r, g, b, 0, null);
    }

    public VertexConsumer delegate() {
        return delegate;
    }

    // ---------------------------------------------------------- tinted entry points

    @Override
    public void quad(MatrixStack.Entry entry, BakedQuad quad, float red, float green, float blue, float alpha,
                     int light, int overlay) {
        if (!perVertex || world == null || blockPos == null) {
            float r = red * uniformTr;
            float g = green * uniformTg;
            float b = blue * uniformTb;
            if (ambientCube != null && dev.puffspark.lightframe.config.ColorLightConfig.get().entityDirectionalLighting) {
                Direction face = quad.getFace();
                Vec3i faceVec = face.getVector();
                Matrix3f normMat = entry.getNormalMatrix();
                Vector3f normal = new Vector3f((float) faceVec.getX(), (float) faceVec.getY(), (float) faceVec.getZ());
                normal.mul(normMat);
                float[] tint = new float[3];
                ambientCube.computeTint(normal.x(), normal.y(), normal.z(), tint);
                r = red * tint[0];
                g = green * tint[1];
                b = blue * tint[2];
            }
            int blockLight = (light & 0xFFFF) >> 4;
            int skyLight = (light >> 16) & 0xFFFF;
            int finalBlockLight = Math.max(blockLight, fallbackBoost);
            int finalLight = (skyLight << 16) | (finalBlockLight << 4);
            delegate.quad(entry, quad, r, g, b, alpha, finalLight, overlay);
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

            int blockLight = (light & 0xFFFF) >> 4;
            int skyLight = (light >> 16) & 0xFFFF;
            int finalBlockLight = Math.max(blockLight, boost);
            int finalLight = (skyLight << 16) | (finalBlockLight << 4);

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
            float r = red * uniformTr;
            float g = green * uniformTg;
            float b = blue * uniformTb;
            if (ambientCube != null && dev.puffspark.lightframe.config.ColorLightConfig.get().entityDirectionalLighting) {
                Direction face = quad.getFace();
                Vec3i faceVec = face.getVector();
                Matrix3f normMat = entry.getNormalMatrix();
                Vector3f normal = new Vector3f((float) faceVec.getX(), (float) faceVec.getY(), (float) faceVec.getZ());
                normal.mul(normMat);
                float[] tint = new float[3];
                ambientCube.computeTint(normal.x(), normal.y(), normal.z(), tint);
                r = red * tint[0];
                g = green * tint[1];
                b = blue * tint[2];
            }
            int[] boostedLights = lights;
            if (fallbackBoost > 0) {
                boostedLights = new int[lights.length];
                for (int i = 0; i < lights.length; i++) {
                    int bl = (lights[i] & 0xFFFF) >> 4;
                    int sl = (lights[i] >> 16) & 0xFFFF;
                    boostedLights[i] = (sl << 16) | (Math.max(bl, fallbackBoost) << 4);
                }
            }
            delegate.quad(entry, quad, brightness, r, g, b, alpha, boostedLights, overlay, useWorldLight);
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
            int blockLight = (vertexLight & 0xFFFF) >> 4;
            int skyLight = (vertexLight >> 16) & 0xFFFF;
            int finalBlockLight = Math.max(blockLight, boost);
            int finalLight = (skyLight << 16) | (finalBlockLight << 4);

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
                Math.min(255, Math.max(0, Math.round(red * uniformTr))),
                Math.min(255, Math.max(0, Math.round(green * uniformTg))),
                Math.min(255, Math.max(0, Math.round(blue * uniformTb))),
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
        int a = (argb >> 24) & 0xFF;
        int r = Math.min(255, Math.max(0, Math.round(((argb >> 16) & 0xFF) * uniformTr)));
        int g = Math.min(255, Math.max(0, Math.round(((argb >> 8) & 0xFF) * uniformTg)));
        int b = Math.min(255, Math.max(0, Math.round((argb & 0xFF) * uniformTb)));
        return delegate.color((a << 24) | (r << 16) | (g << 8) | b);
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
            int blockLight = (uv & 0xFFFF) >> 4;
            int newBlock = Math.max(blockLight, fallbackBoost);
            uv = (uv & 0xFFFF0000) | (newBlock << 4);
        }
        return delegate.light(uv);
    }

    @Override
    public VertexConsumer light(int u, int v) {
        if (fallbackBoost > 0) {
            int currentBlock = u >> 4;
            u = Math.max(currentBlock, fallbackBoost) << 4;
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

    @Override
    public void vertex(float x, float y, float z, int color,
                       float u, float v, int overlay, int light,
                       float normalX, float normalY, float normalZ) {
        int a = (color >> 24) & 0xFF;
        int cr = (color >> 16) & 0xFF;
        int cg = (color >> 8) & 0xFF;
        int cb = color & 0xFF;
        float tr = uniformTr;
        float tg = uniformTg;
        float tb = uniformTb;
        if (ambientCube != null && dev.puffspark.lightframe.config.ColorLightConfig.get().entityDirectionalLighting) {
            float[] tint = new float[3];
            ambientCube.computeTint(normalX, normalY, normalZ, tint);
            tr = tint[0];
            tg = tint[1];
            tb = tint[2];
        }
        int nr = Math.min(255, Math.max(0, Math.round(cr * tr)));
        int ng = Math.min(255, Math.max(0, Math.round(cg * tg)));
        int nb = Math.min(255, Math.max(0, Math.round(cb * tb)));
        int finalColor = (a << 24) | (nr << 16) | (ng << 8) | nb;

        int blockLight = (light & 0xFFFF) >> 4;
        int skyLight = (light >> 16) & 0xFFFF;
        int finalBlockLight = Math.max(blockLight, fallbackBoost);
        int finalLight = (skyLight << 16) | (finalBlockLight << 4);
        delegate.vertex(x, y, z, finalColor, u, v, overlay, finalLight, normalX, normalY, normalZ);
    }
}
