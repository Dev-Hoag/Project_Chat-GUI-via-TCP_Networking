package server.command;

// Command interface - encapsulate request thành object
public interface Command {
    void execute();
    String getName();
}
