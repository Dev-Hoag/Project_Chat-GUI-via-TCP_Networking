package server.handler;

import common.Protocol;
import server.ClientHandler;
import server.router.MessageRouter;

// Strategy xá»­ lÃ½ TYPING_START / TYPING_STOP
public class TypingStrategy implements MessageStrategy {
    private final MessageRouter router;

    public TypingStrategy(MessageRouter router) {
        this.router = router;
    }

    @Override
    public void handle(ClientHandler sender, Protocol.ParsedMessage message) {
        String conversationType = message.field(0);
        String conversationId = message.field(1);
        String receiver = message.field(2);
        boolean typing = Protocol.TYPING_START.equals(message.getCommand());
        router.handleTyping(sender, conversationType, conversationId, receiver, typing);
    }
}
