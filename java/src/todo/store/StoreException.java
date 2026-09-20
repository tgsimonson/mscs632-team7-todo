package todo.store;

/** Carries the exit code the spec assigns to each failure class. */
public class StoreException extends RuntimeException {
    private final int code;

    public StoreException(String message, int code) {
        super(message);
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static StoreException usage(String message) {
        return new StoreException(message, 1);
    }

    public static StoreException notFound(String message) {
        return new StoreException(message, 2);
    }

    public static StoreException storage(String message) {
        return new StoreException(message, 3);
    }
}
