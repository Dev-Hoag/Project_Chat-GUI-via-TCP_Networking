package server.observer;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

// Server broadcaster - gửi sự kiện toàn server tới tất cả observer
public class ServerBroadcaster {
    private final List<ServerObserver> observers = new CopyOnWriteArrayList<>();
    
    public void subscribe(ServerObserver observer) {
        if (!observers.contains(observer)) {
            observers.add(observer);
        }
    }
    
    public void unsubscribe(ServerObserver observer) {
        observers.remove(observer);
    }
    
    public void notifyUserJoined(String username) {
        for (ServerObserver obs : observers) {
            obs.onUserJoined(username);
        }
    }
    
    public void notifyUserLeft(String username) {
        for (ServerObserver obs : observers) {
            obs.onUserLeft(username);
        }
    }
    
    public void notifyUserListUpdated(String csvUsers) {
        for (ServerObserver obs : observers) {
            obs.onUserListUpdated(csvUsers);
        }
    }
    
    public void notifyRoomListUpdated(String roomList) {
        for (ServerObserver obs : observers) {
            obs.onRoomListUpdated(roomList);
        }
    }
}
