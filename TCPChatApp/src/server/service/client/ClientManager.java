package server.service.client;

import common.Protocol;
import server.ClientHandler;
import server.core.ChatRoom;
import server.observer.ClientRoomObserver;
import server.service.client.IClientManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ClientManager implements IClientManager {
    // Quản lý client đang online và các room hiện có trên server.
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<String, ClientHandler>();
    private final Map<String, ChatRoom> rooms = new ConcurrentHashMap<String, ChatRoom>();
    private final Map<String, ClientRoomObserver> roomObservers = new ConcurrentHashMap<String, ClientRoomObserver>();

    public ClientManager() {
        rooms.put(Protocol.LOBBY_ROOM_ID, new ChatRoom(Protocol.LOBBY_ROOM_ID, Protocol.LOBBY_ROOM_NAME));
    }

    // Thêm user mới vào danh sách online, đồng thời cho vào room lobby mặc định.
    public boolean addClient(String username, ClientHandler handler) {
        ClientHandler previous = clients.putIfAbsent(username, handler);
        if (previous == null) {
            ClientRoomObserver observer = new ClientRoomObserver(handler, this);
            roomObservers.put(username, observer);
            ChatRoom lobby = getLobby();
            lobby.addObserver(observer);
            lobby.addMember(username);
            return true;
        }
        return false;
    }

    // Xóa user khỏi danh sách online và remove khỏi tất cả room đã tham gia.
    public void removeClient(String username) {
        if (username == null || username.trim().isEmpty()) {
            return;
        }
        ClientRoomObserver observer = roomObservers.remove(username);
        clients.remove(username);
        for (ChatRoom room : rooms.values()) {
            if (observer != null) {
                room.removeObserver(observer);
            }
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

    public void attachObserverToRoom(String username, ChatRoom room) {
        ClientRoomObserver observer = roomObservers.get(username);
        if (room != null && observer != null) {
            room.addObserver(observer);
        }
    }

    public ChatRoom createRoom(String roomName, Set<String> members) {
        String roomId = "room_" + System.currentTimeMillis();
        return createRoom(roomId, roomName, members);
    }

    public ChatRoom createRoom(String roomId, String roomName, Set<String> members) {
        ChatRoom existing = rooms.get(roomId);
        if (existing != null) {
            for (String member : members) {
                attachObserverToRoom(member, existing);
                existing.addMember(member);
            }
            return existing;
        }
        ChatRoom room = new ChatRoom(roomId, roomName);
        for (String member : members) {
            room.addMember(member);
        }
        attachObservers(room);
        rooms.put(roomId, room);
        return room;
    }

    public Collection<ChatRoom> getRoomsForUser(String username) {
        return rooms.values().stream()
                .filter(room -> room.hasMember(username))
                .collect(Collectors.toList());
    }

    private void attachObservers(ChatRoom room) {
        for (String member : room.getMembers()) {
            ClientRoomObserver observer = roomObservers.get(member);
            if (observer != null) {
                room.addObserver(observer);
            }
        }
    }
}
