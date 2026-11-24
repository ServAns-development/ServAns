package xyz.matumaka.servans.paper.collect;

import java.util.concurrent.atomic.AtomicInteger;

public final class ActivityCounters {
    private final AtomicInteger joins = new AtomicInteger();
    private final AtomicInteger leaves = new AtomicInteger();

    public void joined() {
        joins.incrementAndGet();
    }

    public void left() {
        leaves.incrementAndGet();
    }

    public int takeJoins() {
        return joins.getAndSet(0);
    }

    public int takeLeaves() {
        return leaves.getAndSet(0);
    }
}
