package server.command;

// Concrete Command - bọc một action để router chỉ còn dispatch.
public class RouterCommand implements Command {
    private final String name;
    private final Runnable action;

    public RouterCommand(String name, Runnable action) {
        this.name = name;
        this.action = action;
    }

    @Override
    public void execute() {
        action.run();
    }

    @Override
    public String getName() {
        return name;
    }
}
