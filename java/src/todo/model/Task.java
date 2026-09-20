package todo.model;

/**
 * A single task. Fields are private with accessors, and the mutable ones
 * change only through the methods below.
 */
public class Task {
    private final String id;
    private final String title;
    private final String category;
    private Status status;
    private String assignee;   // user id, or null when unassigned
    private final String createdAt;

    public Task(String id, String title, String category, Status status,
                String assignee, String createdAt) {
        this.id = id;
        this.title = title;
        this.category = category;
        this.status = status;
        this.assignee = assignee;
        this.createdAt = createdAt;
    }

    public String id() { return id; }
    public String title() { return title; }
    public String category() { return category; }
    public Status status() { return status; }
    public String assignee() { return assignee; }
    public String createdAt() { return createdAt; }

    public void complete() {
        this.status = Status.COMPLETE;
    }

    public void assignTo(String userId) {
        this.assignee = userId;
    }
}
