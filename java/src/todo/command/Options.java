package todo.command;

import todo.store.StoreException;

import java.util.Map;

/** Helpers shared by the command implementations. */
public final class Options {

    private Options() {
    }

    public static String require(Map<String, String> options, String name) {
        String value = options.get(name);
        if (value == null || value.isEmpty()) {
            throw StoreException.usage("missing required option --" + name);
        }
        return value;
    }
}
