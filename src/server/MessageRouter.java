package server;

import common.ConversationType;
import common.MessageType;
import common.Protocol;

import java.text.SimpleDateFormat;
import java.io.File;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class MessageRouter {
    private final ClientManager clientManager;
    private final DatabaseManager databaseManager;

    public MessageRouter(ClientManager clientManager, DatabaseManager databaseManager) {
        this.clientManager = clientManager;
        this.databaseManager = databaseManager;
    }

    public void sendInitialState(ClientHandler handler) {
        handler.send(Protocol.build(Protocol.LOGIN_SUCCESS, handler.getUsername()));
        restoreConversations(handler.getUsername());
        sendHistory(handler, ConversationType.LOBBY, Protocol.LOBBY_ROOM_ID);
        broadcastJoin(handler.getUsername());
        broadcastUserList();
        sendRoomListToAllRoomMembers();
        sendPrivateList(handler);
    }

    public void handle(ClientHandler sender, Protocol.ParsedMessage message) {
        String command = message.getCommand();
        if (Protocol.ROOM_MSG.equals(command)) {
            handleRoomMessage(sender, message.field(0), message.field(1));
        } else if (Protocol.PRIVATE_MSG.equals(command)) {
            handlePrivateMessage(sender, message.field(0), message.field(1));
        } else if (Protocol.HISTORY_REQUEST.equals(command)) {
            sendHistory(sender, message.field(0), message.field(1));
        } else if (Protocol.CREATE_ROOM.equals(command)) {
            handleCreateRoom(sender, message.field(0), message.field(1));
        } else if (Protocol.JOIN_ROOM.equals(command)) {
            handleJoinRoom(sender, message.field(0));
        } else if (Protocol.LEAVE_ROOM.equals(command)) {
            handleLeaveRoom(sender, message.field(0));
        } else {
            sender.send(Protocol.build(Protocol.ERROR, "Unsupported command: " + command));
        }
    }

    public void handleFile(ClientHandler sender, String conversationType, String conversationId, String receiver,
                           String fileName, long fileSize) throws Exception {
        if (fileSize > Protocol.MAX_FILE_SIZE_BYTES) {
            sender.send(Protocol.build(Protocol.ERROR, "File must be smaller than 10MB"));
            return;
        }
        if (!canSendToConversation(sender.getUsername(), conversationType, conversationId, receiver)) {
            sender.send(Protocol.build(Protocol.ERROR, "Cannot send file to this conversation"));
            return;
        }

        FileService.StoredFile storedFile = sender.getFileService().receiveFile(sender.getInputStream(), sender.getUsername(), fileName, fileSize);
        String actualConversationId = conversationId;
        String actualReceiver = receiver == null || receiver.trim().isEmpty() ? null : receiver;
        if (ConversationType.PRIVATE.equals(conversationType)) {
            actualConversationId = Protocol.privateConversationId(sender.getUsername(), receiver);
        }

        databaseManager.saveFileMessage(sender.getUsername(), conversationType, actualConversationId, actualReceiver,
                storedFile.originalFileName, storedFile.savedPath);
        String time = currentTime();
        String line = Protocol.build(Protocol.FILE_DELIVER, conversationType, actualConversationId, sender.getUsername(),
                storedFile.originalFileName, storedFile.savedPath, time);

        if (ConversationType.PRIVATE.equals(conversationType)) {
            ClientHandler receiverHandler = clientManager.getClient(receiver);
            sender.send(line);
            if (receiverHandler != null) {
                receiverHandler.send(line);
            }
        } else {
            broadcastToRoom(actualConversationId, line);
        }
    }

    public void handleFileDownload(ClientHandler requester, String fileName, String filePath) {
        try {
            File file = requester.getFileService().getDownloadFile(filePath);
            synchronized (requester) {
                Protocol.writeLine(requester.getOutputStream(),
                        Protocol.build(Protocol.FILE_DOWNLOAD_META, fileName, String.valueOf(file.length())));
                requester.getFileService().writeFileToOutput(file, requester.getOutputStream());
            }
        } catch (Exception e) {
            requester.send(Protocol.build(Protocol.ERROR, "Cannot download file: " + e.getMessage()));
        }
    }

    public void onClientDisconnected(String username) {
        if (username == null || username.trim().isEmpty()) {
            return;
        }
        clientManager.removeClient(username);
        broadcastLeave(username);
        broadcastUserList();
        sendRoomListToAllRoomMembers();
    }

    private void handleRoomMessage(ClientHandler sender, String roomId, String content) {
        if (content.trim().isEmpty()) {
            return;
        }
        ChatRoom room = clientManager.getRoom(roomId);
        if (room == null || !room.hasMember(sender.getUsername())) {
            sender.send(Protocol.build(Protocol.ERROR, "You are not a member of this room"));
            return;
        }
        String conversationType = Protocol.LOBBY_ROOM_ID.equals(roomId) ? ConversationType.LOBBY : ConversationType.ROOM;
        databaseManager.saveTextMessage(sender.getUsername(), conversationType, roomId, null, content);
        broadcastToRoom(roomId, Protocol.build(Protocol.ROOM_MSG_DELIVER, roomId, sender.getUsername(), content, currentTime()));
    }

    private void handlePrivateMessage(ClientHandler sender, String receiver, String content) {
        if (receiver == null || receiver.trim().isEmpty() || content.trim().isEmpty()) {
            return;
        }
        if (receiver.equals(sender.getUsername())) {
            sender.send(Protocol.build(Protocol.ERROR, "Cannot send private message to yourself"));
            return;
        }
        ClientHandler receiverHandler = clientManager.getClient(receiver);
        if (receiverHandler == null) {
            sender.send(Protocol.build(Protocol.ERROR, "User is offline"));
            return;
        }
        String conversationId = Protocol.privateConversationId(sender.getUsername(), receiver);
        String time = currentTime();
        databaseManager.savePrivateConversation(conversationId, sender.getUsername(), receiver);
        databaseManager.saveTextMessage(sender.getUsername(), ConversationType.PRIVATE, conversationId, receiver, content);
        receiverHandler.send(Protocol.build(Protocol.PRIVATE_MSG_DELIVER, sender.getUsername(), content, time));
        sender.send(Protocol.build(Protocol.PRIVATE_MSG_SENT, receiver, content, time));
    }

    private void handleCreateRoom(ClientHandler sender, String roomName, String csvUsers) {
        Set<String> members = new HashSet<String>();
        members.add(sender.getUsername());
        for (String rawUser : csvUsers.split(",")) {
            String username = rawUser.trim();
            if (!username.isEmpty() && clientManager.isOnline(username)) {
                members.add(username);
            }
        }
        if (members.size() < 2) {
            sender.send(Protocol.build(Protocol.ERROR, "Room needs at least 2 online users"));
            return;
        }
        String finalRoomName = roomName == null || roomName.trim().isEmpty() ? "Group Chat" : roomName.trim();
        ChatRoom room = clientManager.createRoom(finalRoomName, members);
        databaseManager.saveRoom(room.getRoomId(), room.getRoomName(), sender.getUsername(), room.getMembers());
        for (String member : room.getMembers()) {
            ClientHandler handler = clientManager.getClient(member);
            if (handler != null) {
                handler.send(Protocol.build(Protocol.ROOM_INVITE, room.getRoomId(), room.getRoomName(), sender.getUsername()));
                sendRoomList(handler);
                sendRoomUsers(handler, room);
            }
        }
        sender.send(Protocol.build(Protocol.CREATE_ROOM_SUCCESS, room.getRoomId(), room.getRoomName()));
    }

    private void handleJoinRoom(ClientHandler sender, String roomId) {
        ChatRoom room = clientManager.getRoom(roomId);
        if (room == null) {
            sender.send(Protocol.build(Protocol.ERROR, "Room does not exist"));
            return;
        }
        room.addMember(sender.getUsername());
        sendRoomList(sender);
        sendRoomUsersToRoom(room);
    }

    private void handleLeaveRoom(ClientHandler sender, String roomId) {
        if (Protocol.LOBBY_ROOM_ID.equals(roomId)) {
            sender.send(Protocol.build(Protocol.ERROR, "Cannot leave lobby"));
            return;
        }
        ChatRoom room = clientManager.getRoom(roomId);
        if (room != null) {
            room.removeMember(sender.getUsername());
            sendRoomList(sender);
            sendRoomUsersToRoom(room);
        }
    }

    private boolean canSendToConversation(String username, String conversationType, String conversationId, String receiver) {
        if (ConversationType.PRIVATE.equals(conversationType)) {
            return receiver != null && clientManager.isOnline(receiver) && !receiver.equals(username);
        }
        ChatRoom room = clientManager.getRoom(conversationId);
        return room != null && room.hasMember(username);
    }

    private void sendHistory(ClientHandler handler, String conversationType, String conversationId) {
        if (conversationType == null || conversationType.trim().isEmpty()) {
            conversationType = ConversationType.LOBBY;
        }
        if (conversationId == null || conversationId.trim().isEmpty()) {
            conversationId = Protocol.LOBBY_ROOM_ID;
        }
        List<DatabaseManager.MessageRecord> records = databaseManager.getRecentHistory(conversationType, conversationId, 20);
        for (DatabaseManager.MessageRecord record : records) {
            handler.send(Protocol.build(Protocol.HISTORY,
                    nullToEmpty(record.conversationType),
                    nullToEmpty(record.conversationId),
                    nullToEmpty(record.sender),
                    nullToEmpty(record.receiver),
                    nullToEmpty(record.messageType),
                    nullToEmpty(record.content),
                    nullToEmpty(record.fileName),
                    nullToEmpty(record.filePath),
                    record.createdAt == null ? "" : record.createdAt.toString()));
        }
    }

    private void broadcastToRoom(String roomId, String line) {
        ChatRoom room = clientManager.getRoom(roomId);
        if (room == null) {
            return;
        }
        for (String member : room.getMembers()) {
            ClientHandler handler = clientManager.getClient(member);
            if (handler != null) {
                handler.send(line);
            }
        }
    }

    private void broadcastJoin(String username) {
        broadcastAll(Protocol.build(Protocol.JOIN, username));
    }

    private void broadcastLeave(String username) {
        broadcastAll(Protocol.build(Protocol.LEAVE, username));
    }

    private void broadcastUserList() {
        broadcastAll(Protocol.build(Protocol.USER_LIST, String.join(",", clientManager.getOnlineUsers())));
    }

    private void broadcastAll(String line) {
        for (ClientHandler handler : clientManager.getAllClients()) {
            handler.send(line);
        }
    }

    private void sendRoomListToAllRoomMembers() {
        for (ClientHandler handler : clientManager.getAllClients()) {
            sendRoomList(handler);
        }
    }

    private void sendRoomList(ClientHandler handler) {
        String rooms = clientManager.getRoomsForUser(handler.getUsername()).stream()
                .map(room -> room.getRoomId() + ":" + room.getRoomName())
                .collect(Collectors.joining(","));
        handler.send(Protocol.build(Protocol.ROOM_LIST, rooms));
    }

    private void restoreConversations(String username) {
        for (DatabaseManager.RoomRecord roomRecord : databaseManager.getRoomsForUser(username)) {
            Set<String> members = new HashSet<String>(databaseManager.getRoomMembers(roomRecord.roomId));
            clientManager.createRoom(roomRecord.roomId, roomRecord.roomName, members);
        }
    }

    private void sendPrivateList(ClientHandler handler) {
        String privateList = databaseManager.getPrivateConversationsForUser(handler.getUsername()).stream()
                .map(record -> record.conversationId + ":" + record.otherUser)
                .collect(Collectors.joining(","));
        handler.send(Protocol.build(Protocol.PRIVATE_LIST, privateList));
    }

    private void sendRoomUsersToRoom(ChatRoom room) {
        for (String member : room.getMembers()) {
            ClientHandler handler = clientManager.getClient(member);
            if (handler != null) {
                sendRoomUsers(handler, room);
            }
        }
    }

    private void sendRoomUsers(ClientHandler handler, ChatRoom room) {
        handler.send(Protocol.build(Protocol.ROOM_USERS, room.getRoomId(), String.join(",", room.getMembers())));
    }

    private String currentTime() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
