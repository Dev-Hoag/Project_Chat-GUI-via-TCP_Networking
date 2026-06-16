package server.service.chat;

import common.ConversationType;
import common.MessageType;
import common.Protocol;
import server.ClientHandler;
import server.core.ChatRoom;
import server.observer.ServerBroadcaster;
import server.service.client.IClientManager;
import server.service.database.IDatabase;
import server.service.file.IFileService;
import server.service.user.IUserRepository.UserRecord;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Orchestrates chat-specific business rules so MessageRouter stays thin.
 */
public class ChatService {
    private final IClientManager clientManager;
    private final IDatabase databaseManager;
    private final ServerBroadcaster broadcaster;

    public ChatService(IClientManager clientManager, IDatabase databaseManager, ServerBroadcaster broadcaster) {
        this.clientManager = clientManager;
        this.databaseManager = databaseManager;
        this.broadcaster = broadcaster;
    }

    public void sendInitialState(ClientHandler handler) {
        handler.send(Protocol.build(Protocol.LOGIN_SUCCESS,
                handler.getUsername(),
                handler.getDisplayName() == null ? handler.getUsername() : handler.getDisplayName(),
                handler.getAvatarPath() == null ? "" : handler.getAvatarPath()));
        restoreConversations(handler.getUsername());
        sendHistory(handler, ConversationType.LOBBY, Protocol.LOBBY_ROOM_ID);
        broadcastJoin(handler.getUsername());
        broadcastUserList();
        sendRoomListToAllRoomMembers();
        sendPrivateList(handler);
    }

    public void broadcastUserList() {
        broadcaster.notifyUserListUpdated(buildUserListPayload());
    }

    public void handleFile(ClientHandler sender, IFileService fileService, String conversationType, String conversationId,
                           String receiver, String fileName, long fileSize) throws Exception {
        if (fileSize > Protocol.MAX_FILE_SIZE_BYTES) {
            sender.send(Protocol.build(Protocol.ERROR, "File must be smaller than 10MB"));
            return;
        }
        if (!canSendToConversation(sender.getUsername(), conversationType, conversationId, receiver)) {
            sender.send(Protocol.build(Protocol.ERROR, "Cannot send file to this conversation"));
            return;
        }

        IFileService.StoredFile storedFile = fileService.receiveFile(
                sender.getInputStream(), sender.getUsername(), fileName, fileSize);

        String actualConversationId = conversationId;
        String actualReceiver = receiver == null || receiver.trim().isEmpty() ? null : receiver;
        if (ConversationType.PRIVATE.equals(conversationType)) {
            actualConversationId = Protocol.privateConversationId(sender.getUsername(), receiver);
        }

        databaseManager.saveFileMessage(sender.getUsername(), conversationType, actualConversationId, actualReceiver,
                storedFile.originalFileName, storedFile.savedPath);

        String line = Protocol.build(Protocol.FILE_DELIVER, conversationType, actualConversationId, sender.getUsername(),
                storedFile.originalFileName, storedFile.savedPath, currentTime());

        if (ConversationType.PRIVATE.equals(conversationType)) {
            ClientHandler receiverHandler = clientManager.getClient(receiver);
            sender.send(line);
            if (receiverHandler != null) {
                receiverHandler.send(line);
            }
            return;
        }

        ChatRoom room = clientManager.getRoom(actualConversationId);
        if (room != null) {
            room.notifyMessageReceived(line);
        }
    }

    public void handleFileDownload(ClientHandler requester, IFileService fileService, String fileName, String filePath) {
        try {
            File file = fileService.getDownloadFile(filePath);
            synchronized (requester) {
                Protocol.writeLine(requester.getOutputStream(),
                        Protocol.build(Protocol.FILE_DOWNLOAD_META, fileName, String.valueOf(file.length())));
                fileService.writeFileToOutput(file, requester.getOutputStream());
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

    public void handleRoomMessage(ClientHandler sender, String roomId, String content,
                                  String replySender, String replyMessageType, String replyContent, String replyFileName) {
        if (content.trim().isEmpty()) {
            return;
        }
        ChatRoom room = clientManager.getRoom(roomId);
        if (room == null || !room.hasMember(sender.getUsername())) {
            sender.send(Protocol.build(Protocol.ERROR, "You are not a member of this room"));
            return;
        }
        String conversationType = Protocol.LOBBY_ROOM_ID.equals(roomId) ? ConversationType.LOBBY : ConversationType.ROOM;
        String time = currentTime();
        String line = buildRoomMessageLine(Protocol.ROOM_MSG_DELIVER, roomId, sender.getUsername(), content, time,
                replySender, replyMessageType, replyContent, replyFileName);
        for (String member : room.getMembers()) {
            if (sender.getUsername().equals(member)) {
                continue;
            }
            ClientHandler target = clientManager.getClient(member);
            if (target != null) {
                target.send(line);
            }
        }
        sender.send(buildRoomMessageLine(Protocol.ROOM_MSG_SENT, roomId, sender.getUsername(), content, time,
                replySender, replyMessageType, replyContent, replyFileName));
        databaseManager.saveTextMessage(sender.getUsername(), conversationType, roomId, null,
                decorateStoredContent(content, replySender, replyMessageType, replyContent, replyFileName));
    }

    public void handlePrivateMessage(ClientHandler sender, String receiver, String content,
                                     String replySender, String replyMessageType, String replyContent, String replyFileName) {
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
        receiverHandler.send(buildPrivateMessageLine(Protocol.PRIVATE_MSG_DELIVER, sender.getUsername(), content, time,
                replySender, replyMessageType, replyContent, replyFileName));
        sender.send(buildPrivateMessageLine(Protocol.PRIVATE_MSG_SENT, receiver, content, time,
                replySender, replyMessageType, replyContent, replyFileName));
        databaseManager.savePrivateConversation(conversationId, sender.getUsername(), receiver);
        databaseManager.saveTextMessage(sender.getUsername(), ConversationType.PRIVATE, conversationId, receiver,
                decorateStoredContent(content, replySender, replyMessageType, replyContent, replyFileName));
    }

    public void handleForwardMessage(ClientHandler sender, String sourceConversationType, String sourceConversationId,
                                     String sourceMessageType, String content, String fileName, String filePath,
                                     String targetConversationType, String targetConversationId, String receiver) {
        if (targetConversationType == null || targetConversationType.trim().isEmpty()) {
            sender.send(Protocol.build(Protocol.ERROR, "Target conversation type is required"));
            return;
        }

        if (!canForwardTarget(sender.getUsername(), targetConversationType, targetConversationId, receiver)) {
            sender.send(Protocol.build(Protocol.ERROR, "Cannot forward to this conversation"));
            return;
        }

        if (MessageType.FILE.equals(sourceMessageType)) {
            forwardFileMessage(sender, targetConversationType, targetConversationId, receiver, fileName, filePath);
            return;
        }

        forwardTextMessage(sender, sourceConversationType, sourceConversationId, targetConversationType,
                targetConversationId, receiver, content);
    }

    public void handleTyping(ClientHandler sender, String conversationType, String conversationId, String receiver, boolean typing) {
        if (ConversationType.PRIVATE.equals(conversationType)) {
            if (receiver == null || receiver.trim().isEmpty() || receiver.equals(sender.getUsername())) {
                return;
            }
            ClientHandler receiverHandler = clientManager.getClient(receiver);
            if (receiverHandler == null) {
                return;
            }
            receiverHandler.send(Protocol.build(Protocol.TYPING_STATUS,
                    ConversationType.PRIVATE,
                    Protocol.privateConversationId(sender.getUsername(), receiver),
                    sender.getUsername(),
                    String.valueOf(typing)));
            return;
        }

        ChatRoom room = clientManager.getRoom(conversationId);
        if (room == null || !room.hasMember(sender.getUsername())) {
            return;
        }
        room.notifyTypingStatusChanged(sender.getUsername(), typing);
    }

    public void handleCreateRoom(ClientHandler sender, String roomName, String csvUsers) {
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

    public void handleJoinRoom(ClientHandler sender, String roomId) {
        ChatRoom room = clientManager.getRoom(roomId);
        if (room == null) {
            sender.send(Protocol.build(Protocol.ERROR, "Room does not exist"));
            return;
        }
        clientManager.attachObserverToRoom(sender.getUsername(), room);
        room.addMember(sender.getUsername());
        sendRoomList(sender);
    }

    public void handleLeaveRoom(ClientHandler sender, String roomId) {
        if (Protocol.LOBBY_ROOM_ID.equals(roomId)) {
            sender.send(Protocol.build(Protocol.ERROR, "Cannot leave lobby"));
            return;
        }
        ChatRoom room = clientManager.getRoom(roomId);
        if (room != null) {
            room.removeMember(sender.getUsername());
            sendRoomList(sender);
        }
    }

    public void sendHistory(ClientHandler handler, String conversationType, String conversationId) {
        if (conversationType == null || conversationType.trim().isEmpty()) {
            conversationType = ConversationType.LOBBY;
        }
        if (conversationId == null || conversationId.trim().isEmpty()) {
            conversationId = Protocol.LOBBY_ROOM_ID;
        }
        List<IDatabase.MessageRecord> records = databaseManager.getRecentHistory(conversationType, conversationId, 20);
        for (IDatabase.MessageRecord record : records) {
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

    private boolean canSendToConversation(String username, String conversationType, String conversationId, String receiver) {
        if (ConversationType.PRIVATE.equals(conversationType)) {
            return receiver != null && clientManager.isOnline(receiver) && !receiver.equals(username);
        }
        ChatRoom room = clientManager.getRoom(conversationId);
        return room != null && room.hasMember(username);
    }

    private boolean canForwardTarget(String username, String conversationType, String conversationId, String receiver) {
        return canSendToConversation(username, conversationType, conversationId, receiver);
    }

    private void forwardTextMessage(ClientHandler sender, String sourceConversationType, String sourceConversationId,
                                    String targetConversationType, String targetConversationId, String receiver,
                                    String content) {
        if (content == null || content.trim().isEmpty()) {
            sender.send(Protocol.build(Protocol.ERROR, "Forward content is empty"));
            return;
        }

        String time = currentTime();
        String forwardPrefix = "[Forwarded from " + sourceConversationType + ":" + sourceConversationId + "] ";
        String forwardedContent = forwardPrefix + content;

        if (ConversationType.PRIVATE.equals(targetConversationType)) {
            ClientHandler receiverHandler = clientManager.getClient(receiver);
            if (receiverHandler == null) {
                sender.send(Protocol.build(Protocol.ERROR, "User is offline"));
                return;
            }
            receiverHandler.send(Protocol.build(Protocol.PRIVATE_MSG_DELIVER, sender.getUsername(), forwardedContent, time));
            sender.send(Protocol.build(Protocol.PRIVATE_MSG_SENT, receiver, forwardedContent, time));
            String conversationId = Protocol.privateConversationId(sender.getUsername(), receiver);
            databaseManager.savePrivateConversation(conversationId, sender.getUsername(), receiver);
            databaseManager.saveTextMessage(sender.getUsername(), ConversationType.PRIVATE, conversationId, receiver, forwardedContent);
            return;
        }

        ChatRoom room = clientManager.getRoom(targetConversationId);
        if (room == null || !room.hasMember(sender.getUsername())) {
            sender.send(Protocol.build(Protocol.ERROR, "Cannot forward to this room"));
            return;
        }
        String line = Protocol.build(Protocol.ROOM_MSG_DELIVER, targetConversationId, sender.getUsername(), forwardedContent, time);
        for (String member : room.getMembers()) {
            if (sender.getUsername().equals(member)) {
                continue;
            }
            ClientHandler target = clientManager.getClient(member);
            if (target != null) {
                target.send(line);
            }
        }
        sender.send(Protocol.build(Protocol.ROOM_MSG_SENT, targetConversationId, forwardedContent, time));
        String conversationType = Protocol.LOBBY_ROOM_ID.equals(targetConversationId) ? ConversationType.LOBBY : ConversationType.ROOM;
        databaseManager.saveTextMessage(sender.getUsername(), conversationType, targetConversationId, null, forwardedContent);
    }

    private void forwardFileMessage(ClientHandler sender, String targetConversationType, String targetConversationId,
                                    String receiver, String fileName, String filePath) {
        if (fileName == null || fileName.trim().isEmpty() || filePath == null || filePath.trim().isEmpty()) {
            sender.send(Protocol.build(Protocol.ERROR, "Forward file metadata is missing"));
            return;
        }

        try {
            File file = sender.getFileService().getDownloadFile(filePath);
            String time = currentTime();
            if (ConversationType.PRIVATE.equals(targetConversationType)) {
                ClientHandler receiverHandler = clientManager.getClient(receiver);
                if (receiverHandler == null) {
                    sender.send(Protocol.build(Protocol.ERROR, "User is offline"));
                    return;
                }
                String line = Protocol.build(Protocol.FILE_DELIVER, ConversationType.PRIVATE,
                        Protocol.privateConversationId(sender.getUsername(), receiver), sender.getUsername(),
                        fileName, file.getPath(), time);
                sender.send(line);
                receiverHandler.send(line);
                databaseManager.savePrivateConversation(Protocol.privateConversationId(sender.getUsername(), receiver),
                        sender.getUsername(), receiver);
                databaseManager.saveFileMessage(sender.getUsername(), ConversationType.PRIVATE,
                        Protocol.privateConversationId(sender.getUsername(), receiver), receiver, fileName, file.getPath());
                return;
            }

            ChatRoom room = clientManager.getRoom(targetConversationId);
            if (room == null || !room.hasMember(sender.getUsername())) {
                sender.send(Protocol.build(Protocol.ERROR, "Cannot forward file to this room"));
                return;
            }
            String line = Protocol.build(Protocol.FILE_DELIVER, targetConversationType, targetConversationId,
                    sender.getUsername(), fileName, file.getPath(), time);
            room.notifyMessageReceived(line);
            String conversationType = Protocol.LOBBY_ROOM_ID.equals(targetConversationId) ? ConversationType.LOBBY : ConversationType.ROOM;
            databaseManager.saveFileMessage(sender.getUsername(), conversationType, targetConversationId, null, fileName, file.getPath());
        } catch (Exception e) {
            sender.send(Protocol.build(Protocol.ERROR, "Cannot forward file: " + e.getMessage()));
        }
    }

    private void broadcastJoin(String username) {
        broadcaster.notifyUserJoined(username);
    }

    private void broadcastLeave(String username) {
        broadcaster.notifyUserLeft(username);
    }

    private String buildRoomMessageLine(String command, String roomId, String sender, String content, String time,
                                        String replySender, String replyMessageType, String replyContent, String replyFileName) {
        return Protocol.build(command, roomId, sender, content, time,
                nullToEmpty(replySender), nullToEmpty(replyMessageType), nullToEmpty(replyContent), nullToEmpty(replyFileName));
    }

    private String buildPrivateMessageLine(String command, String senderOrReceiver, String content, String time,
                                           String replySender, String replyMessageType, String replyContent, String replyFileName) {
        return Protocol.build(command, senderOrReceiver, content, time,
                nullToEmpty(replySender), nullToEmpty(replyMessageType), nullToEmpty(replyContent), nullToEmpty(replyFileName));
    }

    private String decorateStoredContent(String content, String replySender, String replyMessageType,
                                         String replyContent, String replyFileName) {
        if (replySender == null || replySender.trim().isEmpty() || replyMessageType == null || replyMessageType.trim().isEmpty()) {
            return content;
        }
        return Protocol.REPLY_STORAGE_PREFIX
                + Protocol.encode(nullToEmpty(replySender)) + "|"
                + Protocol.encode(nullToEmpty(replyMessageType)) + "|"
                + Protocol.encode(nullToEmpty(replyContent)) + "|"
                + Protocol.encode(nullToEmpty(replyFileName)) + "|"
                + Protocol.encode(nullToEmpty(content));
    }

    private String buildUserListPayload() {
        return clientManager.getAllClients().stream()
                .sorted(Comparator.comparing(ClientHandler::getUsername, String.CASE_INSENSITIVE_ORDER))
                .map(handler -> normalize(handler.getUsername()) + "^" +
                        normalize(handler.getDisplayName() == null ? handler.getUsername() : handler.getDisplayName()) + "^" +
                        normalize(handler.getAvatarPath()))
                .collect(Collectors.joining(","));
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

    private void sendRoomUsers(ClientHandler handler, ChatRoom room) {
        handler.send(Protocol.build(Protocol.ROOM_USERS, room.getRoomId(), String.join(",", room.getMembers())));
    }

    private void restoreConversations(String username) {
        for (IDatabase.RoomRecord roomRecord : databaseManager.getRoomsForUser(username)) {
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

    private String currentTime() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String normalize(String value) {
        return value == null ? "" : value;
    }
}
