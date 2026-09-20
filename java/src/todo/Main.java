package todo;

import todo.command.AddCommand;
import todo.command.AddUserCommand;
import todo.command.AssignCommand;
import todo.command.Command;
import todo.command.Options;
import todo.command.CompleteCommand;
import todo.command.ListCommand;
import todo.command.RemoveCommand;
import todo.command.UsersCommand;
import todo.concurrency.ConcurrencyTest;
import todo.store.StoreException;
import todo.store.TaskStore;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Argument parsing, command dispatch, and exit codes. */
public class Main {

    private static final Map<String, Command> TABLE = new LinkedHashMap<>();

    static {
        TABLE.put("add", new AddCommand());
        TABLE.put("adduser", new AddUserCommand());
        TABLE.put("list", new ListCommand());
        TABLE.put("assign", new AssignCommand());
        TABLE.put("complete", new CompleteCommand());
        TABLE.put("remove", new RemoveCommand());
        TABLE.put("users", new UsersCommand());
    }

    /** Turns --key value pairs into a map; a flag with no value becomes "true". */
    static Map<String, String> parseOptions(List<String> args) {
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = 0; i < args.size(); i++) {
            String token = args.get(i);
            if (!token.startsWith("--")) {
                throw StoreException.usage("unexpected argument " + token);
            }
            String key = token.substring(2);
            if (i + 1 >= args.size() || args.get(i + 1).startsWith("--")) {
                options.put(key, "true");
            } else {
                options.put(key, args.get(i + 1));
                i++;
            }
        }
        return options;
    }

    // splits a line on whitespace while keeping quoted strings together
    static List<String> tokenize(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;

        for (char c : line.toCharArray()) {
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                } else {
                    current.append(c);
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (Character.isWhitespace(c)) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private static final String HELP = String.join("\n",
            "commands:",
            "  add --title \"<text>\" --category <cat> [--assignee <userId>]",
            "  list [--user <id>] [--category <cat>] [--status pending|complete]",
            "  assign --id <taskId> --user <userId>",
            "  complete --id <taskId>",
            "  remove --id <taskId>",
            "  adduser --name <name>",
            "  users",
            "  concurrency-test --workers <n> --ops <n> [--no-lock]",
            "  help",
            "  exit");

    private static String runConcurrency(TaskStore store, Map<String, String> options) {
        int workers;
        int ops;
        try {
            workers = Integer.parseInt(Options.require(options, "workers"));
            ops = Integer.parseInt(Options.require(options, "ops"));
        } catch (NumberFormatException e) {
            throw StoreException.usage("--workers and --ops must be positive integers");
        }
        if (workers < 1 || ops < 1) {
            throw StoreException.usage("--workers and --ops must be positive integers");
        }
        boolean lock = !options.containsKey("no-lock");
        return ConcurrencyTest.run(store, workers, ops, lock);
    }

    private static String dispatch(TaskStore store, String verb, List<String> rest) {
        if (verb.equals("concurrency-test")) {
            return runConcurrency(store, parseOptions(rest));
        }
        Command command = TABLE.get(verb);
        if (command == null) {
            throw StoreException.usage("unknown command " + verb);
        }
        return command.execute(store, parseOptions(rest));
    }

    private static void shell(TaskStore store, String user) throws Exception {
        String prompt = user == null ? "todo> " : "todo(" + user + ")> ";
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        System.out.println("collaborative to-do list, interactive session");
        System.out.println("type help for commands, exit to quit\n");
        System.out.print(prompt);

        String line;
        while ((line = reader.readLine()) != null) {
            List<String> tokens = tokenize(line.trim());

            if (tokens.isEmpty()) {
                System.out.print(prompt);
                continue;
            }
            String verb = tokens.get(0);
            if (verb.equals("exit") || verb.equals("quit")) {
                break;
            }
            if (verb.equals("help")) {
                System.out.println(HELP);
                System.out.print(prompt);
                continue;
            }

            try {
                String output = dispatch(store, verb, tokens.subList(1, tokens.size()));
                if (output != null && !output.isEmpty()) {
                    System.out.println(output);
                }
            } catch (StoreException e) {
                System.out.println("error: " + e.getMessage());
            } catch (RuntimeException e) {
                System.out.println("error: " + e.getMessage());
            }
            System.out.print(prompt);
        }
        System.out.println("session ended");
    }

    public static void main(String[] args) throws Exception {
        Path dataFile = Path.of(System.getProperty("todo.data", "data/tasks.json"));
        TaskStore store = new TaskStore(dataFile);

        if (args.length == 0) {
            System.err.println("error: no command given, expected one of "
                    + String.join(", ", TABLE.keySet()) + ", concurrency-test, shell");
            System.exit(1);
        }

        String verb = args[0];
        List<String> rest = new ArrayList<>(List.of(args).subList(1, args.length));

        if (verb.equals("shell")) {
            shell(store, parseOptions(rest).get("user"));
            System.exit(0);
        }

        try {
            String output = dispatch(store, verb, rest);
            if (output != null && !output.isEmpty()) {
                System.out.println(output);
            }
            System.exit(0);
        } catch (StoreException e) {
            System.err.println("error: " + e.getMessage());
            System.exit(e.code());
        } catch (RuntimeException e) {
            System.err.println("error: " + e.getMessage());
            System.exit(1);
        }
    }
}
