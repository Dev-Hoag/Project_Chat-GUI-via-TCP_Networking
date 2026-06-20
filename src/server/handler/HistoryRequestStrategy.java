package server.handler;

import common.Protocol;
import server.ClientHandler;
import server.router.MessageRouter;

// Strategy xử lý HISTORY_REQUEST
public class HistoryRequestStrategy implements MessageStrategy {
    private final MessageRouter router;
    
    public HistoryRequestStrategy(MessageRouter router) {
        this.router = router;
    }
    
    @Override
    public void handle(ClientHandler sender, Protocol.ParsedMessage message) {
        String conversationType = message.field(0);
        String conversationId = message.field(1);
        router.sendHistory(sender, conversationType, conversationId);
    }
}
