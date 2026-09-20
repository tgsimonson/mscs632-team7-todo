package todo.command;

import todo.store.TaskStore;

import java.util.Map;

public class CompleteCommand implements Command {
    @Override
    public String execute(TaskStore store, Map<String, String> options) {
        String id = Options.require(options, "id");
        // completing an already complete task is not an error
        store.update(snapshot -> snapshot.task(id).complete());
        return "completed " + id;
    }
}
