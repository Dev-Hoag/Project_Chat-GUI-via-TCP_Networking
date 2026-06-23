package server.handler;

import common.Protocol;
import server.ClientHandler;
import server.router.MessageRouter;

// Strategy xử lý LEAVE_ROOM
public class LeaveRoomStrategy implements MessageStrategy {
    private final MessageRouter router;
    
    public LeaveRoomStrategy(MessageRouter router) {
        this.router = router;
    }
    
    @Override
    public void handle(ClientHandler sender, Protocol.ParsedMessage message) {
        String roomId = message.field(0);
        router.handleLeaveRoom(sender, roomId);
    }
}
