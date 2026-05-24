package server;

import common.Protocol;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ClientManager {
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<String, ClientHandler>();
    private final Map<String, ChatRoom> rooms = new ConcurrentHashMap<String, ChatRoom>();

    public ClientManager() {
        rooms.put(Protocol.LOBBY_ROOM_ID, new ChatRoom(Protocol.LOBBY_ROOM_ID, Protocol.LOBBY_ROOM_NAME));
    }

    public boolean addClient(String username, ClientHandler handler) {
        ClientHandler previous = clients.putIfAbsent(username, handler);
        if (previous == null) {
            getLobby().addMember(username);
            return true;
        }
        return false;
    }

    public void removeClient(String username) {
        if (username == null || username.trim().isEmpty()) {
            return;
        }
        clients.remove(username);
        for (ChatRoom room : rooms.values()) {
            room.removeMember(username);
        }
    }

    public ClientHandler getClient(String username) {
        return clients.get(username);
    }

    public boolean isOnline(String username) {
        return clients.containsKey(username);
    }

    public Collection<ClientHandler> getAllClients() {
        return clients.values();
    }

    public List<String> getOnlineUsers() {
        List<String> users = new ArrayList<String>(clients.keySet());
        Collections.sort(users);
        return users;
    }

    public ChatRoom getLobby() {
        return rooms.get(Protocol.LOBBY_ROOM_ID);
    }

    public ChatRoom getRoom(String roomId) {
        return rooms.get(roomId);
    }

    public ChatRoom createRoom(String roomName, Set<String> members) {
        String roomId = "room_" + System.currentTimeMillis();
        return createRoom(roomId, roomName, members);
    }

    public ChatRoom createRoom(String roomId, String roomName, Set<String> members) {
        ChatRoom existing = rooms.get(roomId);
        if (existing != null) {
            for (String member : members) {
                existing.addMember(member);
            }
            return existing;
        }
        ChatRoom room = new ChatRoom(roomId, roomName);
        for (String member : members) {
            room.addMember(member);
        }
        rooms.put(roomId, room);
        return room;
    }

    public Collection<ChatRoom> getRoomsForUser(String username) {
        return rooms.values().stream()
                .filter(room -> room.hasMember(username))
                .collect(Collectors.toList());
    }
}
