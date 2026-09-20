package todo.command;

import todo.model.Status;
import todo.model.Task;
import todo.store.StoreException;
import todo.store.TaskStore;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ListCommand implements Command {
    @Override
    public String execute(TaskStore store, Map<String, String> options) {
        String status = options.get("status");
        if (status != null && !status.equals("pending") && !status.equals("complete")) {
            throw StoreException.usage("--status must be pending or complete");
        }

        TaskStore.Snapshot snapshot = store.read();
        String user = options.get("user");
        if (user != null) {
            snapshot.user(user);
        }
        String category = options.get("category");

        // filters combine with and
        List<Task> matched = snapshot.tasks.stream()
                .filter(t -> user == null || user.equals(t.assignee()))
                .filter(t -> category == null || category.equals(t.category()))
                .filter(t -> status == null || t.status() == Status.fromWire(status))
                .collect(Collectors.toList());

        return Formatter.table(matched, snapshot.users);
    }
}
