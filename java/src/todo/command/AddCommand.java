package todo.command;

import todo.model.Status;
import todo.model.Task;
import todo.store.StoreException;
import todo.store.TaskStore;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class AddCommand implements Command {
    @Override
    public String execute(TaskStore store, Map<String, String> options) {
        String title = Options.require(options, "title");
        String category = Options.require(options, "category");
        String assignee = options.get("assignee");

        if (title.length() > 120) {
            throw StoreException.usage("title exceeds 120 characters");
        }

        AtomicReference<String> newId = new AtomicReference<>();
        store.update(snapshot -> {
            if (assignee != null) {
                snapshot.user(assignee);
            }
            String id = snapshot.nextTaskId();
            newId.set(id);
            snapshot.tasks.add(new Task(
                    id,
                    title,
                    category.toLowerCase(),
                    Status.PENDING,
                    assignee,
                    Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()));
        });

        return "added " + newId.get();
    }
}
