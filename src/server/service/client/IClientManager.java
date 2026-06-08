package server.service.client;

import server.core.ChatRoom;
import server.ClientHandler;
import java.util.Collection;
import java.util.List;
import java.util.Set;

// Interface cho client manager - dễ test và mock
public interface IClientManager {
    // Client management
    boolean addClient(String username, ClientHandler handler);
    void removeClient(String username);
    ClientHandler getClient(String username);
    boolean isOnline(String username);
    Collection<ClientHandler> getAllClients();
    List<String> getOnlineUsers();
    
    // Room management
    ChatRoom getLobby();
    ChatRoom getRoom(String roomId);
    void attachObserverToRoom(String username, ChatRoom room);
    ChatRoom createRoom(String roomName, Set<String> members);
    ChatRoom createRoom(String roomId, String roomName, Set<String> members);
    Collection<ChatRoom> getRoomsForUser(String username);
}
