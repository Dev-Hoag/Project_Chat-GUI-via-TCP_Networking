package server.handler;

import common.Protocol;
import server.ClientHandler;
import server.router.MessageRouter;

// Strategy xử lý JOIN_ROOM
public class JoinRoomStrategy implements MessageStrategy {
    private final MessageRouter router;
    
    public JoinRoomStrategy(MessageRouter router) {
        this.router = router;
    }
    
    @Override
    public void handle(ClientHandler sender, Protocol.ParsedMessage message) {
        String roomId = message.field(0);
        router.handleJoinRoom(sender, roomId);
    }
}
