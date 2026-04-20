package com.trafficgame.engine.util;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe unique ID generator.
 */
public final class IdGenerator {

    private final AtomicLong counter;

    public IdGenerator() {
        this(0);
    }

    public IdGenerator(long startFrom) {
        this.counter = new AtomicLong(startFrom);
    }

    public long next() {
        return counter.incrementAndGet();
    }

    public String nextString() {
        return String.valueOf(next());
    }

    public long current() {
        return counter.get();
    }
}
