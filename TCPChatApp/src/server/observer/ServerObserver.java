package server.observer;

// Observer interface - client lắng nghe sự kiện từ server (join/leave)
public interface ServerObserver {
    void onUserJoined(String username);
    void onUserLeft(String username);
    void onUserListUpdated(String csvUsers);
    void onRoomListUpdated(String roomList);
}
