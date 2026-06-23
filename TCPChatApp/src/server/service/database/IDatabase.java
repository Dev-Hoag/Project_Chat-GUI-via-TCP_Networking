package server.service.database;

import java.util.List;

// Interface cho database service - dễ test và mock
public interface IDatabase {
    // Text message
    void saveTextMessage(String sender, String conversationType, String conversationId, String receiver, String content);
    
    // File message
    void saveFileMessage(String sender, String conversationType, String conversationId, String receiver, String fileName, String filePath);
    
    // Room management
    void saveRoom(String roomId, String roomName, String createdBy, Iterable<String> members);
    List<RoomRecord> getRoomsForUser(String username);
    List<String> getRoomMembers(String roomId);
    
    // Private conversation
    void savePrivateConversation(String conversationId, String userA, String userB);
    List<PrivateConversationRecord> getPrivateConversationsForUser(String username);
    
    // History
    List<MessageRecord> getRecentHistory(String conversationType, String conversationId, int limit);
    
    // Status
    boolean isConfigured();
    
    // DTO Classes
    public static class MessageRecord {
        public final String sender;
        public final String conversationType;
        public final String conversationId;
        public final String receiver;
        public final String messageType;
        public final String content;
        public final String fileName;
        public final String filePath;
        public final java.sql.Timestamp createdAt;
        
        public MessageRecord(String sender, String conversationType, String conversationId, String receiver,
                           String messageType, String content, String fileName, String filePath, java.sql.Timestamp createdAt) {
            this.sender = sender;
            this.conversationType = conversationType;
            this.conversationId = conversationId;
            this.receiver = receiver;
            this.messageType = messageType;
            this.content = content;
            this.fileName = fileName;
            this.filePath = filePath;
            this.createdAt = createdAt;
        }
    }
    
    public static class RoomRecord {
        public final String roomId;
        public final String roomName;
        
        public RoomRecord(String roomId, String roomName) {
            this.roomId = roomId;
            this.roomName = roomName;
        }
    }
    
    public static class PrivateConversationRecord {
        public final String conversationId;
        public final String otherUser;
        
        public PrivateConversationRecord(String conversationId, String otherUser) {
            this.conversationId = conversationId;
            this.otherUser = otherUser;
        }
    }
}
