package todo.command;

import todo.store.TaskStore;

import java.util.Map;

/**
 * One verb of the command line interface.
 *
 * An interface with one implementation per verb is the object oriented
 * counterpart to the JavaScript dispatch table of functions. Dispatch is a map
 * lookup in both, but here each entry carries its own type.
 */
public interface Command {
    /** Returns the text to print, or null to print nothing. */
    String execute(TaskStore store, Map<String, String> options);
}
