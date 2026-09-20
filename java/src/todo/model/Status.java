package todo.model;

/**
 * Task status. An enum rather than a string so that an invalid state cannot
 * be constructed, which is the static counterpart to JavaScript validating
 * the string at runtime.
 */
public enum Status {
    PENDING("pending"),
    COMPLETE("complete");

    private final String wire;

    Status(String wire) {
        this.wire = wire;
    }

    /** the exact token written to and read from tasks.json */
    public String wire() {
        return wire;
    }

    public static Status fromWire(String s) {
        for (Status status : values()) {
            if (status.wire.equals(s)) {
                return status;
            }
        }
        throw new IllegalArgumentException("unknown status " + s);
    }
}
