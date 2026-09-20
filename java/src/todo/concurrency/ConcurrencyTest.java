package todo.concurrency;

import todo.model.Status;
import todo.model.Task;
import todo.store.TaskStore;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * N worker threads each perform M add operations against the same store.
 *
 * With the locks held the final count must equal the starting count plus N
 * times M. Without them two failures are possible and both are reported rather
 * than thrown: lost updates, where a thread writes a snapshot taken before
 * another thread's write, and torn reads, where a thread reads after the file
 * has been truncated but before the new bytes land.
 *
 * Unlike the JavaScript implementation, these are real operating system
 * threads running in parallel, so the interleaving is genuine preemption
 * rather than cooperative yielding at await points.
 */
public final class ConcurrencyTest {

    private ConcurrencyTest() {
    }

    public static String run(TaskStore store, int workers, int ops, boolean lock) {
        int starting = store.read().tasks.size();
        int expected = starting + workers * ops;

        AtomicInteger failed = new AtomicInteger();
        AtomicInteger torn = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(workers);
        for (int w = 1; w <= workers; w++) {
            final int id = w;
            pool.submit(() -> {
                for (int i = 0; i < ops; i++) {
                    final int n = i;
                    try {
                        store.update(snapshot -> snapshot.tasks.add(new Task(
                                snapshot.nextTaskId(),
                                "worker " + id + " task " + n,
                                "load",
                                Status.PENDING,
                                null,
                                Instant.now().truncatedTo(ChronoUnit.SECONDS).toString())), lock);
                    } catch (RuntimeException e) {
                        failed.incrementAndGet();
                        if (e.getMessage() != null && e.getMessage().contains("parse")) {
                            torn.incrementAndGet();
                        }
                    }
                }
            });
        }

        pool.shutdown();
        try {
            if (!pool.awaitTermination(5, TimeUnit.MINUTES)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pool.shutdownNow();
        }

        Integer actual = null;
        String readError = null;
        try {
            actual = store.read().tasks.size();
        } catch (RuntimeException e) {
            readError = e.getMessage();
        }

        List<String> lines = new ArrayList<>();
        lines.add("workers: " + workers + "  ops each: " + ops
                + "  locking: " + (lock ? "enabled" : "disabled"));
        lines.add("expected tasks: " + expected);
        lines.add("actual tasks:   " + (actual == null ? "unreadable" : actual));
        lines.add("result: " + (actual != null && actual == expected ? "PASS" : "FAIL"));

        if (actual == null) {
            lines.add("store corrupted: unsynchronized writes left invalid JSON");
            lines.add("read error: " + readError);
        } else if (actual != expected) {
            lines.add("lost updates: " + (expected - actual));
        }

        if (!lock && failed.get() > 0) {
            lines.add("failed operations: " + failed.get() + " (" + torn.get() + " torn reads)");
        }

        return String.join("\n", lines);
    }
}
