package todo.store;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

/**
 * Cross process advisory lock.
 *
 * The lock is a file created with CREATE_NEW, which fails if the path already
 * exists. That check and create is atomic at the syscall level, so exactly one
 * process wins. The JavaScript implementation acquires the same lock the same
 * way. FileChannel.lock would be the more idiomatic Java choice, but an OS
 * advisory lock and an exclusive create do not exclude one another, so the
 * mechanism is fixed in the spec rather than chosen per language.
 */
public final class FileLock implements AutoCloseable {

    private static final long RETRY_MS = 5;
    private static final long TIMEOUT_MS = 10_000;
    private static final long STALE_MS = 30_000;

    private final Path path;

    private FileLock(Path path) {
        this.path = path;
    }

    /** Blocks until the lock is held, then returns a handle that releases it. */
    public static FileLock acquire(Path path) throws IOException {
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;

        while (true) {
            try {
                Files.createFile(path);
                Files.writeString(path, String.valueOf(ProcessHandle.current().pid()));
                return new FileLock(path);
            } catch (FileAlreadyExistsException e) {
                if (System.currentTimeMillis() > deadline) {
                    if (breakIfStale(path)) {
                        continue;
                    }
                    throw new IOException("timed out waiting for " + path);
                }
                try {
                    Thread.sleep(RETRY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted waiting for " + path, ie);
                }
            }
        }
    }

    // removes a lock left behind by a process that died holding it
    private static boolean breakIfStale(Path path) {
        try {
            FileTime modified = Files.getLastModifiedTime(path);
            if (System.currentTimeMillis() - modified.toMillis() > STALE_MS) {
                Files.deleteIfExists(path);
                return true;
            }
        } catch (IOException ignored) {
            // the holder released it while we were looking, which is fine
        }
        return false;
    }

    /** try with resources guarantees release even when the body throws */
    @Override
    public void close() throws IOException {
        Files.deleteIfExists(path);
    }
}
