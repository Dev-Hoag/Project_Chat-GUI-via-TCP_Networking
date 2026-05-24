package server;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ChatRoom {
    private final String roomId;
    private final String roomName;
    private final Set<String> members = ConcurrentHashMap.newKeySet();

    public ChatRoom(String roomId, String roomName) {
        this.roomId = roomId;
        this.roomName = roomName;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getRoomName() {
        return roomName;
    }

    public void addMember(String username) {
        members.add(username);
    }

    public void removeMember(String username) {
        members.remove(username);
    }

    public boolean hasMember(String username) {
        return members.contains(username);
    }

    public Set<String> getMembers() {
        return Collections.unmodifiableSet(members);
    }
}
