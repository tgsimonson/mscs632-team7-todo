package todo.command;

import todo.store.TaskStore;

import java.util.Map;

public class UsersCommand implements Command {
    @Override
    public String execute(TaskStore store, Map<String, String> options) {
        return Formatter.userList(store.read().users);
    }
}
