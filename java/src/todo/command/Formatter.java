package todo.command;

import todo.model.Task;
import todo.model.User;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Fixed width table rendering. Column widths are fixed by the spec so both
 * implementations produce identical stdout.
 */
public final class Formatter {

    private static final int ID_WIDTH = 6;
    private static final int STATUS_WIDTH = 10;
    private static final int CATEGORY_WIDTH = 12;
    private static final int ASSIGNEE_WIDTH = 12;

    private Formatter() {
    }

    private static String pad(String value, int width) {
        if (value.length() >= width) {
            return value;
        }
        return value + " ".repeat(width - value.length());
    }

    public static String header() {
        return pad("ID", ID_WIDTH)
                + pad("STATUS", STATUS_WIDTH)
                + pad("CATEGORY", CATEGORY_WIDTH)
                + pad("ASSIGNEE", ASSIGNEE_WIDTH)
                + "TITLE";
    }

    private static String row(Task task, List<User> users) {
        String assignee = "-";
        if (task.assignee() != null) {
            Optional<User> user = users.stream()
                    .filter(u -> u.id().equals(task.assignee()))
                    .findFirst();
            assignee = user.map(User::name).orElse(task.assignee());
        }
        return pad(task.id(), ID_WIDTH)
                + pad(task.status().wire(), STATUS_WIDTH)
                + pad(task.category(), CATEGORY_WIDTH)
                + pad(assignee, ASSIGNEE_WIDTH)
                + task.title();
    }

    /** Sorted by createdAt then id, as the spec requires. */
    public static String table(List<Task> tasks, List<User> users) {
        if (tasks.isEmpty()) {
            return "no tasks match";
        }
        StringBuilder sb = new StringBuilder(header());
        tasks.stream()
                .sorted(Comparator.comparing(Task::createdAt).thenComparing(Task::id))
                .forEach(t -> sb.append("\n").append(row(t, users)));
        return sb.toString();
    }

    public static String userList(List<User> users) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < users.size(); i++) {
            if (i > 0) {
                sb.append("\n");
            }
            sb.append(pad(users.get(i).id(), ID_WIDTH)).append(users.get(i).name());
        }
        return sb.toString();
    }
}
