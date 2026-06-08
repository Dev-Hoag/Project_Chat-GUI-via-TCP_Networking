package server.decorator;

// Interface cho socket handler - dễ decorator
public interface SocketHandler {
    void send(String line);
}
