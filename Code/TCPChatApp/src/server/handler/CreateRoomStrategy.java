package server.handler;

import common.Protocol;
import server.ClientHandler;
import server.router.MessageRouter;

// Strategy xử lý CREATE_ROOM
public class CreateRoomStrategy implements MessageStrategy {
    private final MessageRouter router;
    
    public CreateRoomStrategy(MessageRouter router) {
        this.router = router;
    }
    
    @Override
    public void handle(ClientHandler sender, Protocol.ParsedMessage message) {
        String roomName = message.field(0);
        String csvUsers = message.field(1);
        router.handleCreateRoom(sender, roomName, csvUsers);
    }
}
