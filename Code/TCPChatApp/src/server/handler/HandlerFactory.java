package server.handler;

import common.Protocol;
import server.router.MessageRouter;
import java.util.HashMap;
import java.util.Map;

// Factory tạo strategy cho từng command
public class HandlerFactory {
    private final Map<String, MessageStrategy> strategies = new HashMap<>();
    
    public HandlerFactory(MessageRouter router) {
        // Đăng ký tất cả strategy
        strategies.put(Protocol.ROOM_MSG, new RoomMessageStrategy(router));
        strategies.put(Protocol.PRIVATE_MSG, new PrivateMessageStrategy(router));
        strategies.put(Protocol.FORWARD_MSG, new ForwardMessageStrategy(router));
        strategies.put(Protocol.HISTORY_REQUEST, new HistoryRequestStrategy(router));
        strategies.put(Protocol.CREATE_ROOM, new CreateRoomStrategy(router));
        strategies.put(Protocol.JOIN_ROOM, new JoinRoomStrategy(router));
        strategies.put(Protocol.LEAVE_ROOM, new LeaveRoomStrategy(router));
        strategies.put(Protocol.TYPING_START, new TypingStrategy(router));
        strategies.put(Protocol.TYPING_STOP, new TypingStrategy(router));
    }
    
    // Lấy strategy cho command
    public MessageStrategy getStrategy(String command) {
        return strategies.get(command);
    }
    
    // Đăng ký strategy mới (nếu cần)
    public void registerStrategy(String command, MessageStrategy strategy) {
        strategies.put(command, strategy);
    }
}
