package todo.command;

import todo.store.TaskStore;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class AssignCommand implements Command {
    @Override
    public String execute(TaskStore store, Map<String, String> options) {
        String id = Options.require(options, "id");
        String userId = Options.require(options, "user");

        AtomicReference<String> name = new AtomicReference<>();
        store.update(snapshot -> {
            var task = snapshot.task(id);
            var user = snapshot.user(userId);
            task.assignTo(user.id());
            name.set(user.name());
        });

        return "assigned " + id + " to " + name.get();
    }
}
