package server.decorator;

// Decorator thêm validation trước khi gửi message
public class ValidationHandler implements SocketHandler {
    private final SocketHandler wrapped;
    
    public ValidationHandler(SocketHandler wrapped) {
        this.wrapped = wrapped;
    }
    
    @Override
    public void send(String line) {
        // Validate trước khi gửi
        if (line == null || line.isEmpty()) {
            System.err.println("[VALIDATION] Error: Empty message");
            return;
        }
        if (line.length() > 1000000) {
            System.err.println("[VALIDATION] Error: Message too large");
            return;
        }
        wrapped.send(line);
    }
}
