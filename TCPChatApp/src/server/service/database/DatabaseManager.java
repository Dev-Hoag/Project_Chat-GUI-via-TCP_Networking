package server.service.database;

import common.MessageType;
import server.service.database.IDatabase;
import server.service.user.IUserRepository;
import server.service.user.IUserRepository.UserRecord;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Properties;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Repository-style persistence layer for messages, rooms, and private conversations.
 */
public class DatabaseManager implements IDatabase, IUserRepository {
    private final String url;
    private final String user;
    private final String password;
    private final boolean configured;
    private final DatabaseDialect dialect;
    private final DatabaseSchemaInitializer schemaInitializer;

    public DatabaseManager() {
        DatabaseConfig config = DatabaseConfig.load();
        this.url = config.getUrl();
        this.user = config.getUser();
        this.password = config.getPassword();
        this.configured = config.isConfigured();
        this.dialect = config.getDialect();
        this.schemaInitializer = new DatabaseSchemaInitializer();

        if (!configured) {
            System.out.println("[WARN] Database config is not fully configured. Server will run without DB persistence.");
            return;
        }

        loadJdbcDriver();
        initialize();
    }

    public boolean isConfigured() {
        return configured;
    }

    public boolean usernameExists(String username) {
        if (!configured) {
            return false;
        }
        String sql = "SELECT 1 FROM users WHERE username = ?";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            System.out.println("[DB] Cannot check username: " + e.getMessage());
            return false;
        }
    }

    public UserRecord getUserByUsername(String username) {
        if (!configured) {
            return null;
        }
        String sql = "SELECT id, username, display_name, avatar_path, is_active, created_at, updated_at, last_login_at " +
                "FROM users WHERE username = ?";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapUser(resultSet);
                }
            }
        } catch (SQLException e) {
            System.out.println("[DB] Cannot load user: " + e.getMessage());
        }
        return null;
    }

    public UserRecord createUser(String username, String displayName, String avatarPath) {
        if (!configured) {
            return null;
        }
        if (dialect == DatabaseDialect.MYSQL) {
            String sql = "INSERT INTO users (username, display_name, avatar_path) VALUES (?, ?, ?)";
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, username);
                statement.setString(2, displayName);
                statement.setString(3, avatarPath);
                statement.executeUpdate();
            } catch (SQLException e) {
                System.out.println("[DB] Cannot create user: " + e.getMessage());
                return null;
            }
            return getUserByUsername(username);
        }

        String sql = "INSERT INTO users (username, display_name, avatar_path) VALUES (?, ?, ?) " +
                "RETURNING id, username, display_name, avatar_path, is_active, created_at, updated_at, last_login_at";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, displayName);
            statement.setString(3, avatarPath);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapUser(resultSet);
                }
            }
        } catch (SQLException e) {
            System.out.println("[DB] Cannot create user: " + e.getMessage());
        }
        return null;
    }

    public void updateLastLogin(String username) {
        updateUserTimestamp(username, "last_login_at = CURRENT_TIMESTAMP");
    }

    public void updateAvatarPath(String username, String avatarPath) {
        if (!configured) {
            return;
        }
        String sql = "UPDATE users SET avatar_path = ?, updated_at = CURRENT_TIMESTAMP WHERE username = ?";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, avatarPath);
            statement.setString(2, username);
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println("[DB] Cannot update avatar: " + e.getMessage());
        }
    }

    public void updateDisplayName(String username, String displayName) {
        if (!configured) {
            return;
        }
        String sql = "UPDATE users SET display_name = ?, updated_at = CURRENT_TIMESTAMP WHERE username = ?";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, displayName);
            statement.setString(2, username);
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println("[DB] Cannot update display name: " + e.getMessage());
        }
    }

    public java.util.List<UserRecord> getAllUsers() {
        if (!configured) {
            return Collections.emptyList();
        }
        String sql = "SELECT id, username, display_name, avatar_path, is_active, created_at, updated_at, last_login_at FROM users ORDER BY username";
        java.util.List<UserRecord> users = new ArrayList<UserRecord>();
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                users.add(mapUser(resultSet));
            }
        } catch (SQLException e) {
            System.out.println("[DB] Cannot load users: " + e.getMessage());
        }
        return users;
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
        String roomSql = dialect == DatabaseDialect.MYSQL
                ? "INSERT INTO rooms (room_id, room_name, created_by) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE room_name = VALUES(room_name)"
                : "INSERT INTO rooms (room_id, room_name, created_by) VALUES (?, ?, ?) " +
                "ON CONFLICT (room_id) DO UPDATE SET room_name = EXCLUDED.room_name";
        String memberSql = dialect == DatabaseDialect.MYSQL
                ? "INSERT IGNORE INTO room_members (room_id, username) VALUES (?, ?)"
                : "INSERT INTO room_members (room_id, username) VALUES (?, ?) ON CONFLICT DO NOTHING";
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

    public List<IDatabase.RoomRecord> getRoomsForUser(String username) {
        if (!configured) {
            return Collections.emptyList();
        }
        String sql = "SELECT r.room_id, r.room_name FROM rooms r " +
                "JOIN room_members rm ON r.room_id = rm.room_id WHERE rm.username = ? ORDER BY r.created_at";
        List<IDatabase.RoomRecord> rooms = new ArrayList<IDatabase.RoomRecord>();
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rooms.add(new IDatabase.RoomRecord(resultSet.getString("room_id"), resultSet.getString("room_name")));
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
        String sql = dialect == DatabaseDialect.MYSQL
                ? "INSERT IGNORE INTO private_conversations (conversation_id, user_a, user_b) VALUES (?, ?, ?)"
                : "INSERT INTO private_conversations (conversation_id, user_a, user_b) VALUES (?, ?, ?) ON CONFLICT DO NOTHING";
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

    public List<IDatabase.PrivateConversationRecord> getPrivateConversationsForUser(String username) {
        if (!configured) {
            return Collections.emptyList();
        }
        String sql = "SELECT conversation_id, user_a, user_b FROM private_conversations " +
                "WHERE user_a = ? OR user_b = ? ORDER BY created_at";
        List<IDatabase.PrivateConversationRecord> records = new ArrayList<IDatabase.PrivateConversationRecord>();
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String userA = resultSet.getString("user_a");
                    String userB = resultSet.getString("user_b");
                    String otherUser = username.equals(userA) ? userB : userA;
                    records.add(new IDatabase.PrivateConversationRecord(resultSet.getString("conversation_id"), otherUser));
                }
            }
        } catch (SQLException e) {
            System.out.println("[DB] Cannot load private conversations: " + e.getMessage());
        }
        return records;
    }

    public List<IDatabase.MessageRecord> getRecentHistory(String conversationType, String conversationId, int limit) {
        if (!configured) {
            return Collections.emptyList();
        }
        String sql = "SELECT sender, conversation_type, conversation_id, receiver, message_type, content, file_name, file_path, created_at " +
                "FROM messages WHERE conversation_type = ? AND conversation_id = ? ORDER BY created_at DESC LIMIT ?";
        List<IDatabase.MessageRecord> records = new ArrayList<IDatabase.MessageRecord>();
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, conversationType);
            statement.setString(2, conversationId);
            statement.setInt(3, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    records.add(new IDatabase.MessageRecord(
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
        int attempts = 3;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try (Connection connection = getConnection()) {
                schemaInitializer.initialize(connection, dialect);
                return;
            } catch (SQLException e) {
                System.out.println("[DB] Cannot initialize database (attempt " + attempt + "/" + attempts + "): " + e.getMessage());
                if (attempt < attempts) {
                    try {
                        Thread.sleep(2000L);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
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
        Properties props = new Properties();
        props.setProperty("user", user);
        props.setProperty("password", password);
        props.setProperty("connectTimeout", "15");
        props.setProperty("socketTimeout", "30");
        props.setProperty("tcpKeepAlive", "true");
        return DriverManager.getConnection(url, props);
    }

    private void loadJdbcDriver() {
        String driverClass = dialect == DatabaseDialect.MYSQL
                ? "com.mysql.cj.jdbc.Driver"
                : "org.postgresql.Driver";
        try {
            Class.forName(driverClass);
            System.out.println("[DB] Using " + dialect.name() + " (" + maskJdbcUrl(url) + ")");
        } catch (ClassNotFoundException e) {
            System.out.println("[DB] JDBC driver not found: " + driverClass);
        }
    }

    private String maskJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null) {
            return "";
        }
        int queryIndex = jdbcUrl.indexOf('?');
        return queryIndex > 0 ? jdbcUrl.substring(0, queryIndex) : jdbcUrl;
    }

    private UserRecord mapUser(ResultSet resultSet) throws SQLException {
        return new UserRecord(
                resultSet.getLong("id"),
                resultSet.getString("username"),
                resultSet.getString("display_name"),
                resultSet.getString("avatar_path"),
                resultSet.getBoolean("is_active"),
                resultSet.getTimestamp("created_at"),
                resultSet.getTimestamp("updated_at"),
                resultSet.getTimestamp("last_login_at")
        );
    }

    private void updateUserTimestamp(String username, String clause) {
        if (!configured) {
            return;
        }
        String sql = "UPDATE users SET " + clause + ", updated_at = CURRENT_TIMESTAMP WHERE username = ?";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println("[DB] Cannot update user timestamp: " + e.getMessage());
        }
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
