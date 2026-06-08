package server.handler;

import common.Protocol;
import server.ClientHandler;
import server.router.MessageRouter;

// Strategy xử lý ROOM_MSG
public class RoomMessageStrategy implements MessageStrategy {
    private final MessageRouter router;
    
    public RoomMessageStrategy(MessageRouter router) {
        this.router = router;
    }
    
    @Override
    public void handle(ClientHandler sender, Protocol.ParsedMessage message) {
        String roomId = message.field(0);
        String content = message.field(1);
        router.handleRoomMessage(sender, roomId, content);
    }
}
