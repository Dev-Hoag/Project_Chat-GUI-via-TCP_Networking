package server.handler;

import common.Protocol;
import server.ClientHandler;
import server.router.MessageRouter;

// Strategy xử lý FORWARD_MSG
public class ForwardMessageStrategy implements MessageStrategy {
    private final MessageRouter router;

    public ForwardMessageStrategy(MessageRouter router) {
        this.router = router;
    }

    @Override
    public void handle(ClientHandler sender, Protocol.ParsedMessage message) {
        router.handleForwardMessage(
                sender,
                message.field(0),
                message.field(1),
                message.field(2),
                message.field(3),
                message.field(4),
                message.field(5),
                message.field(6),
                message.field(7),
                message.field(8)
        );
    }
}
