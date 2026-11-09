package app.rayclient.lite;

import java.util.concurrent.atomic.AtomicInteger;

/** Invalidate stale START workers when STOP, revoke, or teardown happens. */
final class LiteGeneration {
    private final AtomicInteger value = new AtomicInteger();
    int start() { return value.incrementAndGet(); }
    void cancel() { value.incrementAndGet(); }
    boolean current(int token) { return value.get() == token; }
}
