package server;

import common.ConversationType;
import common.MessageType;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatabaseManager {
    private static final String ENV_URL = "SUPABASE_DB_URL";
    private static final String ENV_USER = "SUPABASE_DB_USER";
    private static final String ENV_PASSWORD = "SUPABASE_DB_PASSWORD";

    private final String url;
    private final String user;
    private final String password;
    private final boolean configured;

    public DatabaseManager() {
        Map<String, String> envFile = loadDotEnv();
        this.url = getConfigValue(ENV_URL, envFile);
        this.user = getConfigValue(ENV_USER, envFile);
        this.password = getConfigValue(ENV_PASSWORD, envFile);
        this.configured = isNotBlank(url) && isNotBlank(user) && isNotBlank(password);
        if (!configured) {
            System.out.println("[WARN] Supabase DB config is not fully configured. Server will run without DB persistence.");
            return;
        }
        loadPostgresDriver();
        initialize();
    }

    public boolean isConfigured() {
        return configured;
    }

    public void saveTextMessage(String sender, String conversationType, String conversationId, String receiver, String content) {
        saveMessage(sender, conversationType, conversationId, receiver, MessageType.TEXT, content, null, null);
    }

    public void saveFileMessage(String sender, String conversationType, String conversationId, String receiver,
                                String fileName, String filePath) {
        saveMessage(sender, conversationType, conversationId, receiver, MessageType.FILE, null, fileName, filePath);
    }

    public void saveRoom(String roomId, String roomName, String createdBy, Iterable<String> members) {
        if (!configured) {
            return;
        }
        String roomSql = "INSERT INTO rooms (room_id, room_name, created_by) VALUES (?, ?, ?) " +
                "ON CONFLICT (room_id) DO UPDATE SET room_name = EXCLUDED.room_name";
        String memberSql = "INSERT INTO room_members (room_id, username) VALUES (?, ?) ON CONFLICT DO NOTHING";
        try (Connection connection = getConnection();
             PreparedStatement roomStatement = connection.prepareStatement(roomSql);
             PreparedStatement memberStatement = connection.prepareStatement(memberSql)) {
            roomStatement.setString(1, roomId);
            roomStatement.setString(2, roomName);
            roomStatement.setString(3, createdBy);
            roomStatement.executeUpdate();

            for (String member : members) {
                memberStatement.setString(1, roomId);
                memberStatement.setString(2, member);
                memberStatement.addBatch();
            }
            memberStatement.executeBatch();
        } catch (SQLException e) {
            System.out.println("[DB] Cannot save room: " + e.getMessage());
        }
    }

    public List<RoomRecord> getRoomsForUser(String username) {
        if (!configured) {
            return Collections.emptyList();
        }
        String sql = "SELECT r.room_id, r.room_name FROM rooms r " +
                "JOIN room_members rm ON r.room_id = rm.room_id WHERE rm.username = ? ORDER BY r.created_at";
        List<RoomRecord> rooms = new ArrayList<RoomRecord>();
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rooms.add(new RoomRecord(resultSet.getString("room_id"), resultSet.getString("room_name")));
                }
            }
        } catch (SQLException e) {
            System.out.println("[DB] Cannot load rooms: " + e.getMessage());
        }
        return rooms;
    }

    public List<String> getRoomMembers(String roomId) {
        if (!configured) {
            return Collections.emptyList();
        }
        String sql = "SELECT username FROM room_members WHERE room_id = ?";
        List<String> members = new ArrayList<String>();
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, roomId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    members.add(resultSet.getString("username"));
                }
            }
        } catch (SQLException e) {
            System.out.println("[DB] Cannot load room members: " + e.getMessage());
        }
        return members;
    }

    public void savePrivateConversation(String conversationId, String userA, String userB) {
        if (!configured) {
            return;
        }
        String sql = "INSERT INTO private_conversations (conversation_id, user_a, user_b) VALUES (?, ?, ?) ON CONFLICT DO NOTHING";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, conversationId);
            statement.setString(2, userA);
            statement.setString(3, userB);
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println("[DB] Cannot save private conversation: " + e.getMessage());
        }
    }

    public List<PrivateConversationRecord> getPrivateConversationsForUser(String username) {
        if (!configured) {
            return Collections.emptyList();
        }
        String sql = "SELECT conversation_id, user_a, user_b FROM private_conversations " +
                "WHERE user_a = ? OR user_b = ? ORDER BY created_at";
        List<PrivateConversationRecord> records = new ArrayList<PrivateConversationRecord>();
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String userA = resultSet.getString("user_a");
                    String userB = resultSet.getString("user_b");
                    String otherUser = username.equals(userA) ? userB : userA;
                    records.add(new PrivateConversationRecord(resultSet.getString("conversation_id"), otherUser));
                }
            }
        } catch (SQLException e) {
            System.out.println("[DB] Cannot load private conversations: " + e.getMessage());
        }
        return records;
    }

    public List<MessageRecord> getRecentHistory(String conversationType, String conversationId, int limit) {
        if (!configured) {
            return Collections.emptyList();
        }
        String sql = "SELECT sender, conversation_type, conversation_id, receiver, message_type, content, file_name, file_path, created_at " +
                "FROM messages WHERE conversation_type = ? AND conversation_id = ? ORDER BY created_at DESC LIMIT ?";
        List<MessageRecord> records = new ArrayList<MessageRecord>();
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, conversationType);
            statement.setString(2, conversationId);
            statement.setInt(3, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    records.add(new MessageRecord(
                            resultSet.getString("sender"),
                            resultSet.getString("conversation_type"),
                            resultSet.getString("conversation_id"),
                            resultSet.getString("receiver"),
                            resultSet.getString("message_type"),
                            resultSet.getString("content"),
                            resultSet.getString("file_name"),
                            resultSet.getString("file_path"),
                            resultSet.getTimestamp("created_at")
                    ));
                }
            }
        } catch (SQLException e) {
            System.out.println("[DB] Cannot load history: " + e.getMessage());
        }
        Collections.reverse(records);
        return records;
    }

    private void initialize() {
        String sql = "CREATE TABLE IF NOT EXISTS messages (" +
                "id BIGSERIAL PRIMARY KEY," +
                "sender TEXT NOT NULL," +
                "conversation_type TEXT NOT NULL DEFAULT '" + ConversationType.LOBBY + "'," +
                "conversation_id TEXT NOT NULL DEFAULT 'lobby'," +
                "receiver TEXT," +
                "message_type TEXT NOT NULL," +
                "content TEXT," +
                "file_name TEXT," +
                "file_path TEXT," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")";
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
            statement.executeUpdate("ALTER TABLE messages ADD COLUMN IF NOT EXISTS conversation_type TEXT NOT NULL DEFAULT '" + ConversationType.LOBBY + "'");
            statement.executeUpdate("ALTER TABLE messages ADD COLUMN IF NOT EXISTS conversation_id TEXT NOT NULL DEFAULT 'lobby'");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS rooms (" +
                    "room_id TEXT PRIMARY KEY," +
                    "room_name TEXT NOT NULL," +
                    "created_by TEXT NOT NULL," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS room_members (" +
                    "room_id TEXT NOT NULL," +
                    "username TEXT NOT NULL," +
                    "PRIMARY KEY (room_id, username)" +
                    ")");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS private_conversations (" +
                    "conversation_id TEXT PRIMARY KEY," +
                    "user_a TEXT NOT NULL," +
                    "user_b TEXT NOT NULL," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");
        } catch (SQLException e) {
            System.out.println("[DB] Cannot initialize database: " + e.getMessage());
        }
    }

    private void saveMessage(String sender, String conversationType, String conversationId, String receiver,
                             String messageType, String content, String fileName, String filePath) {
        if (!configured) {
            return;
        }
        String sql = "INSERT INTO messages (sender, conversation_type, conversation_id, receiver, message_type, content, file_name, file_path) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sender);
            statement.setString(2, conversationType);
            statement.setString(3, conversationId);
            statement.setString(4, receiver);
            statement.setString(5, messageType);
            statement.setString(6, content);
            statement.setString(7, fileName);
            statement.setString(8, filePath);
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println("[DB] Cannot save message: " + e.getMessage());
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    private void loadPostgresDriver() {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            System.out.println("[DB] PostgreSQL JDBC driver not found. Make sure lib/postgresql-jdbc.jar is in the classpath.");
        }
    }

    private String getConfigValue(String key, Map<String, String> envFile) {
        String value = System.getenv(key);
        if (isNotBlank(value)) {
            return value;
        }
        return envFile.get(key);
    }

    private Map<String, String> loadDotEnv() {
        Map<String, String> values = new HashMap<String, String>();
        File file = new File(".env");
        if (!file.exists()) {
            return values;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int equalsIndex = trimmed.indexOf('=');
                if (equalsIndex <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, equalsIndex).trim();
                String value = trimmed.substring(equalsIndex + 1).trim();
                values.put(key, stripQuotes(value));
            }
        } catch (IOException e) {
            System.out.println("[WARN] Cannot read .env file: " + e.getMessage());
        }
        return values;
    }

    private String stripQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public static class MessageRecord {
        public final String sender;
        public final String conversationType;
        public final String conversationId;
        public final String receiver;
        public final String messageType;
        public final String content;
        public final String fileName;
        public final String filePath;
        public final Timestamp createdAt;

        public MessageRecord(String sender, String conversationType, String conversationId, String receiver,
                             String messageType, String content, String fileName, String filePath, Timestamp createdAt) {
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
