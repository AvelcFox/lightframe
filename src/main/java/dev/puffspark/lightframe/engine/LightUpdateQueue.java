package dev.puffspark.lightframe.engine;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Budgeted FIFO of pending light update ops with automatic deduplication.
 * Prevents queue flooding by coalescing multiple updates for the same source.
 */
public final class LightUpdateQueue {

    private final ArrayDeque<LightNode> ops = new ArrayDeque<>(64);
    private final Set<UUID> pendingUpdates = new HashSet<>();

    public synchronized void enqueue(LightNode node) {
        if (node.op == LightNode.REMOVE) {
            pendingUpdates.remove(node.id);
            ops.removeIf(n -> n.id.equals(node.id));
        } else if (node.op == LightNode.UPDATE) {
            if (pendingUpdates.contains(node.id)) {
                return; // Already pending an update: coalesce to avoid queue flooding!
            }
            pendingUpdates.add(node.id);
        }
        ops.addLast(node);
    }

    /**
     * Drains ops while the budget allows.
     * Guarantees at least 15ms so placed/broken torches resolve immediately in the current tick.
     * @return number of processed ops
     */
    public synchronized int process(Consumer<LightNode> applier, long budgetNanos) {
        if (ops.isEmpty()) return 0;

        long effectiveBudget = Math.max(budgetNanos * 4, 15_000_000L);
        long deadline = System.nanoTime() + effectiveBudget;
        int processed = 0;

        while (!ops.isEmpty()) {
            LightNode node = ops.pollFirst();
            if (node != null) {
                if (node.op == LightNode.UPDATE) {
                    pendingUpdates.remove(node.id);
                }
                applier.accept(node);
                processed++;
            }
            if (System.nanoTime() >= deadline) break;
        }
        return processed;
    }

    public synchronized int size() {
        return ops.size();
    }

    public synchronized boolean isEmpty() {
        return ops.isEmpty();
    }

    public synchronized void clear() {
        ops.clear();
        pendingUpdates.clear();
    }
}

