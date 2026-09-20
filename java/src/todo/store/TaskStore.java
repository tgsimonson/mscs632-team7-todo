package todo.store;

import todo.model.Status;
import todo.model.Task;
import todo.model.User;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Owns all access to tasks.json. No other class reads or writes the file.
 *
 * Two layers of exclusion are required and neither alone is sufficient. The
 * ReentrantLock serializes the threads inside this JVM. The FileLock excludes
 * other processes, including the JavaScript implementation running against the
 * same file.
 */
public class TaskStore {

    public static final int SCHEMA_VERSION = 1;

    private final Path dataFile;
    private final Path lockFile;
    private final ReentrantLock mutex = new ReentrantLock();

    public TaskStore(Path dataFile) {
        this.dataFile = dataFile;
        this.lockFile = dataFile.resolveSibling("tasks.lock");
    }

    /** Snapshot of the file contents. */
    public static class Snapshot {
        public final List<User> users;
        public List<Task> tasks;

        Snapshot(List<User> users, List<Task> tasks) {
            this.users = users;
            this.tasks = tasks;
        }

        public Task task(String id) {
            for (Task t : tasks) {
                if (t.id().equals(id)) {
                    return t;
                }
            }
            throw StoreException.notFound("no task with id " + id);
        }

        public User user(String id) {
            for (User u : users) {
                if (u.id().equals(id)) {
                    return u;
                }
            }
            throw StoreException.notFound("no user with id " + id);
        }

        public Optional<User> findUser(String id) {
            return users.stream().filter(u -> u.id().equals(id)).findFirst();
        }

        /** t followed by the highest existing numeric suffix plus one */
        public String nextTaskId() {
            int highest = 0;
            for (Task t : tasks) {
                try {
                    int n = Integer.parseInt(t.id().substring(1));
                    if (n > highest) {
                        highest = n;
                    }
                } catch (NumberFormatException ignored) {
                    // ids that do not follow the pattern do not affect the counter
                }
            }
            return "t" + (highest + 1);
        }
    }

    @SuppressWarnings("unchecked")
    public Snapshot read() {
        String raw;
        try {
            raw = Files.readString(dataFile);
        } catch (IOException e) {
            throw StoreException.storage("cannot read " + dataFile + ": " + e.getMessage());
        }

        Map<String, Object> root;
        try {
            root = (Map<String, Object>) JsonCodec.parse(raw);
        } catch (RuntimeException e) {
            throw StoreException.storage("cannot parse " + dataFile + ": " + e.getMessage());
        }

        Object version = root.get("schemaVersion");
        if (!(version instanceof Long) || ((Long) version).intValue() != SCHEMA_VERSION) {
            throw StoreException.storage(
                    "schemaVersion " + version + " is not supported, expected " + SCHEMA_VERSION);
        }

        List<User> users = new ArrayList<>();
        for (Object entry : (List<Object>) root.get("users")) {
            Map<String, Object> u = (Map<String, Object>) entry;
            users.add(new User((String) u.get("id"), (String) u.get("name")));
        }

        List<Task> tasks = new ArrayList<>();
        for (Object entry : (List<Object>) root.get("tasks")) {
            Map<String, Object> t = (Map<String, Object>) entry;
            tasks.add(new Task(
                    (String) t.get("id"),
                    (String) t.get("title"),
                    (String) t.get("category"),
                    Status.fromWire((String) t.get("status")),
                    (String) t.get("assignee"),
                    (String) t.get("createdAt")));
        }

        return new Snapshot(users, tasks);
    }

    /**
     * Writes the exact format the spec fixes: field order, two space
     * indentation, trailing newline. Built by hand rather than by a library so
     * the output matches the JavaScript implementation byte for byte.
     */
    public String serialize(Snapshot snapshot) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"schemaVersion\": ").append(SCHEMA_VERSION).append(",\n");

        sb.append("  \"users\": [\n");
        for (int i = 0; i < snapshot.users.size(); i++) {
            User u = snapshot.users.get(i);
            sb.append("    {\n");
            sb.append("      \"id\": \"").append(JsonCodec.escape(u.id())).append("\",\n");
            sb.append("      \"name\": \"").append(JsonCodec.escape(u.name())).append("\"\n");
            sb.append("    }").append(i < snapshot.users.size() - 1 ? "," : "").append("\n");
        }
        sb.append("  ],\n");

        sb.append("  \"tasks\": [\n");
        for (int i = 0; i < snapshot.tasks.size(); i++) {
            Task t = snapshot.tasks.get(i);
            sb.append("    {\n");
            sb.append("      \"id\": \"").append(JsonCodec.escape(t.id())).append("\",\n");
            sb.append("      \"title\": \"").append(JsonCodec.escape(t.title())).append("\",\n");
            sb.append("      \"category\": \"").append(JsonCodec.escape(t.category())).append("\",\n");
            sb.append("      \"status\": \"").append(t.status().wire()).append("\",\n");
            sb.append("      \"assignee\": ");
            if (t.assignee() == null) {
                sb.append("null");
            } else {
                sb.append('"').append(JsonCodec.escape(t.assignee())).append('"');
            }
            sb.append(",\n");
            sb.append("      \"createdAt\": \"").append(JsonCodec.escape(t.createdAt())).append("\"\n");
            sb.append("    }").append(i < snapshot.tasks.size() - 1 ? "," : "").append("\n");
        }
        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    public void write(Snapshot snapshot) {
        try {
            Files.writeString(dataFile, serialize(snapshot));
        } catch (IOException e) {
            throw StoreException.storage("cannot write " + dataFile + ": " + e.getMessage());
        }
    }

    /**
     * Runs a read modify write cycle under both locks. Pass lock false to
     * bypass them and demonstrate lost updates.
     */
    public void update(Consumer<Snapshot> body, boolean lock) {
        if (!lock) {
            Snapshot snapshot = read();
            body.accept(snapshot);
            write(snapshot);
            return;
        }

        mutex.lock();
        try (FileLock ignored = FileLock.acquire(lockFile)) {
            Snapshot snapshot = read();
            body.accept(snapshot);
            write(snapshot);
        } catch (IOException e) {
            throw StoreException.storage("lock failure: " + e.getMessage());
        } finally {
            mutex.unlock();
        }
    }

    public void update(Consumer<Snapshot> body) {
        update(body, true);
    }
}
