package todo.command;

import todo.model.User;
import todo.store.StoreException;
import todo.store.TaskStore;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public class AddUserCommand implements Command {

    private static final Pattern VALID_NAME = Pattern.compile("^[a-z0-9_-]+$");

    @Override
    public String execute(TaskStore store, Map<String, String> options) {
        String name = Options.require(options, "name").toLowerCase();

        if (!VALID_NAME.matcher(name).matches()) {
            throw StoreException.usage(
                    "name must be lowercase letters, digits, hyphen or underscore");
        }

        AtomicReference<String> newId = new AtomicReference<>();
        store.update(snapshot -> {
            boolean taken = snapshot.users.stream().anyMatch(u -> u.name().equals(name));
            if (taken) {
                throw StoreException.usage("user " + name + " already exists");
            }
            String id = nextUserId(snapshot);
            newId.set(id);
            snapshot.users.add(new User(id, name));
        });

        return "added user " + newId.get();
    }

    // u followed by the highest existing numeric suffix plus one
    private static String nextUserId(TaskStore.Snapshot snapshot) {
        int highest = 0;
        for (User u : snapshot.users) {
            try {
                int n = Integer.parseInt(u.id().substring(1));
                if (n > highest) {
                    highest = n;
                }
            } catch (NumberFormatException ignored) {
                // ids that do not follow the pattern do not affect the counter
            }
        }
        return "u" + (highest + 1);
    }
}
