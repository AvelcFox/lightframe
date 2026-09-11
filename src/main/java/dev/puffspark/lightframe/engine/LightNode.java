package dev.puffspark.lightframe.engine;

import java.util.UUID;

/**
 * Pending engine op. Carries only the op type and the source id: the actual
 * state is re-read from the (volatile) source fields at execution time, which
 * makes repeated mutations cheap and keeps the final state always authoritative.
 */
public final class LightNode {

    public static final byte ADD = 0;
    public static final byte REMOVE = 1;
    public static final byte UPDATE = 2;

    public final byte op;
    public final UUID id;

    private LightNode(byte op, UUID id) {
        this.op = op;
        this.id = id;
    }

    public static LightNode update(UUID id) {
        return new LightNode(UPDATE, id);
    }

    public static LightNode remove(UUID id) {
        return new LightNode(REMOVE, id);
    }
}

