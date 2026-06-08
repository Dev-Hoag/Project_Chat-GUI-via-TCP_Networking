package server.decorator;

// Decorator thêm logging khi gửi message
public class LoggingHandler implements SocketHandler {
    private final SocketHandler wrapped;
    private final String username;
    
    public LoggingHandler(SocketHandler wrapped, String username) {
        this.wrapped = wrapped;
        this.username = username;
    }
    
    @Override
    public void send(String line) {
        // Ghi log trước khi gửi
        String logMsg = line.length() > 100 ? line.substring(0, 100) + "..." : line;
        System.out.println("[LOG] Sending to " + username + ": " + logMsg);
        wrapped.send(line);
    }
}
