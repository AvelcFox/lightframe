package dev.puffspark.lightframe.render;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;

/**
 * Wraps a {@link VertexConsumerProvider} so every buffer pulled from it is
 * color-tinted. Single-entry layer cache: model renderers typically request
 * one or two layers per entity, so this keeps per-frame allocation near zero.
 */
public final class TintingVertexConsumerProvider implements VertexConsumerProvider {

    private final VertexConsumerProvider delegate;
    private final float tr, tg, tb;
    private final int boost;
    private final AmbientLightCube ambientCube;

    private RenderLayer lastLayer;
    private VertexConsumer lastWrapped;

    public TintingVertexConsumerProvider(VertexConsumerProvider delegate, float r, float g, float b, int boost, AmbientLightCube ambientCube) {
        this.delegate = delegate;
        this.tr = r;
        this.tg = g;
        this.tb = b;
        this.boost = boost;
        this.ambientCube = ambientCube;
    }

    public TintingVertexConsumerProvider(VertexConsumerProvider delegate, float r, float g, float b, int boost) {
        this(delegate, r, g, b, boost, null);
    }

    public TintingVertexConsumerProvider(VertexConsumerProvider delegate, float r, float g, float b) {
        this(delegate, r, g, b, 0, null);
    }

    @Override
    public VertexConsumer getBuffer(RenderLayer layer) {
        VertexConsumer raw = delegate.getBuffer(layer);
        if (raw instanceof TintingVertexConsumer) return raw;
        if (layer == lastLayer && lastWrapped != null) return lastWrapped;
        VertexConsumer wrapped = new TintingVertexConsumer(raw, tr, tg, tb, boost, ambientCube);
        lastLayer = layer;
        lastWrapped = wrapped;
        return wrapped;
    }
}

