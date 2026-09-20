package todo.command;

import todo.store.TaskStore;

import java.util.Map;
import java.util.stream.Collectors;

public class RemoveCommand implements Command {
    @Override
    public String execute(TaskStore store, Map<String, String> options) {
        String id = Options.require(options, "id");
        store.update(snapshot -> {
            snapshot.task(id);
            snapshot.tasks = snapshot.tasks.stream()
                    .filter(t -> !t.id().equals(id))
                    .collect(Collectors.toList());
        });
        return "removed " + id;
    }
}
