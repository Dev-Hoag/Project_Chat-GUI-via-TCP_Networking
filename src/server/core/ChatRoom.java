package server.core;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import server.observer.RoomObserver;

// ChatRoom với Observer Pattern - client tự động nhận sự kiện theo roomId
public class ChatRoom {
    // Quản lý thành viên room
    private final String roomId;
    private final String roomName;
    private final Set<String> members = ConcurrentHashMap.newKeySet();
    
    // Observer pattern - client lắng nghe sự kiện từ room
    private final List<RoomObserver> observers = new CopyOnWriteArrayList<>();
    
    public ChatRoom(String roomId, String roomName) {
        this.roomId = roomId;
        this.roomName = roomName;
    }
    
    // Observer management
    public void addObserver(RoomObserver observer) {
        if (!observers.contains(observer)) {
            observers.add(observer);
        }
    }
    
    public void removeObserver(RoomObserver observer) {
        observers.remove(observer);
    }
    
    // Notify observers khi có message
    public void notifyMessageReceived(String message) {
        for (RoomObserver observer : observers) {
            observer.onMessageReceived(roomId, message);
        }
    }
    
    // Member management
    public void addMember(String username) {
        members.add(username);
        for (RoomObserver observer : observers) {
            observer.onMemberJoined(roomId, username);
        }
    }
    
    public void removeMember(String username) {
        members.remove(username);
        for (RoomObserver observer : observers) {
            observer.onMemberLeft(roomId, username);
        }
    }
    
    public boolean hasMember(String username) {
        return members.contains(username);
    }
    
    public Set<String> getMembers() {
        return Collections.unmodifiableSet(members);
    }
    
    public String getRoomId() {
        return roomId;
    }
    
    public String getRoomName() {
        return roomName;
    }
}
