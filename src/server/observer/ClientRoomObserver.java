package server.observer;

import common.ConversationType;
import common.Protocol;
import server.ClientHandler;
import server.core.ChatRoom;
import server.service.client.IClientManager;

/**
 * Bridges room events to a single client without making ClientHandler itself a RoomObserver.
 */
public class ClientRoomObserver implements RoomObserver {
    private final ClientHandler clientHandler;
    private final IClientManager clientManager;

    public ClientRoomObserver(ClientHandler clientHandler, IClientManager clientManager) {
        this.clientHandler = clientHandler;
        this.clientManager = clientManager;
    }

    @Override
    public void onMessageReceived(String roomId, String message) {
        clientHandler.send(message);
    }

    @Override
    public void onMemberJoined(String roomId, String username) {
        sendRoomUsers(roomId);
    }

    @Override
    public void onMemberLeft(String roomId, String username) {
        sendRoomUsers(roomId);
    }

    @Override
    public void onTypingStatusChanged(String roomId, String username, boolean typing) {
        String conversationType = Protocol.LOBBY_ROOM_ID.equals(roomId) ? ConversationType.LOBBY : ConversationType.ROOM;
        clientHandler.send(Protocol.build(Protocol.TYPING_STATUS, conversationType, roomId, username, String.valueOf(typing)));
    }

    private void sendRoomUsers(String roomId) {
        ChatRoom room = clientManager.getRoom(roomId);
        if (room != null) {
            clientHandler.send(Protocol.build(Protocol.ROOM_USERS, room.getRoomId(), String.join(",", room.getMembers())));
        }
    }
}
