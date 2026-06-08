package server.observer;

// Observer interface - client lắng nghe sự kiện từ room
public interface RoomObserver {
    void onMessageReceived(String roomId, String message);
    void onMemberJoined(String roomId, String username);
    void onMemberLeft(String roomId, String username);
}
