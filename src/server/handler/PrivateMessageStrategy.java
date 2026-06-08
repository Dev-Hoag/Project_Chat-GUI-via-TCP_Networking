package server.handler;

import common.Protocol;
import server.ClientHandler;
import server.router.MessageRouter;

// Strategy xử lý PRIVATE_MSG
public class PrivateMessageStrategy implements MessageStrategy {
    private final MessageRouter router;
    
    public PrivateMessageStrategy(MessageRouter router) {
        this.router = router;
    }
    
    @Override
    public void handle(ClientHandler sender, Protocol.ParsedMessage message) {
        String receiver = message.field(0);
        String content = message.field(1);
        router.handlePrivateMessage(sender, receiver, content);
    }
}
