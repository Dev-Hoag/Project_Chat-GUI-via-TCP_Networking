package server;

import common.MessageType;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
    private static final String CREATE_MESSAGES_TABLE = """
            CREATE TABLE IF NOT EXISTS messages (
                id SERIAL PRIMARY KEY,
                sender TEXT NOT NULL,
                receiver TEXT,
                message_type TEXT NOT NULL,
                content TEXT,
                file_name TEXT,
                file_path TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;

    private final String url;
    private final String user;
    private final String password;
    private final boolean enabled;

    public DatabaseManager() {
        this.url = getenv("SUPABASE_DB_URL", "");
        this.user = getenv("SUPABASE_DB_USER", "postgres");
        this.password = getenv("SUPABASE_DB_PASSWORD", "");
        this.enabled = !url.isBlank() && !password.isBlank();
        if (!enabled) {
            System.out.println("[DB] Supabase database is disabled. Set SUPABASE_DB_URL, SUPABASE_DB_USER, SUPABASE_DB_PASSWORD.");
            return;
        }
        try {
            Class.forName("org.postgresql.Driver");
            initialize();
            System.out.println("[DB] Connected to Supabase PostgreSQL.");
        } catch (ClassNotFoundException | SQLException ex) {
            throw new IllegalStateException("Cannot initialize Supabase PostgreSQL connection", ex);
        }
    }

    private static String getenv(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null ? defaultValue : value;
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    private void initialize() throws SQLException {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(CREATE_MESSAGES_TABLE);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void saveTextMessage(String sender, String receiver, String content) {
        saveMessage(sender, receiver, MessageType.TEXT.name(), content, null, null);
    }

    public void saveFileMessage(String sender, String receiver, String fileName, String filePath) {
        saveMessage(sender, receiver, MessageType.FILE.name(), null, fileName, filePath);
    }

    private void saveMessage(String sender, String receiver, String messageType, String content, String fileName, String filePath) {
        if (!enabled) {
            return;
        }
        String sql = """
                INSERT INTO messages (sender, receiver, message_type, content, file_name, file_path)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sender);
            statement.setString(2, receiver);
            statement.setString(3, messageType);
            statement.setString(4, content);
            statement.setString(5, fileName);
            statement.setString(6, filePath);
            statement.executeUpdate();
        } catch (SQLException ex) {
            System.err.println("[DB] Cannot save message: " + ex.getMessage());
        }
    }

    public List<HistoryRecord> getLatestPublicMessages(int limit) {
        List<HistoryRecord> records = new ArrayList<>();
        if (!enabled) {
            return records;
        }
        String sql = """
                SELECT sender, receiver, message_type, content, file_name, file_path, created_at
                FROM messages
                WHERE receiver IS NULL
                ORDER BY created_at DESC, id DESC
                LIMIT ?
                """;
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    records.add(0, new HistoryRecord(
                            resultSet.getString("sender"),
                            resultSet.getString("receiver"),
                            resultSet.getString("message_type"),
                            resultSet.getString("content"),
                            resultSet.getString("file_name"),
                            resultSet.getString("file_path"),
                            resultSet.getString("created_at")
                    ));
                }
            }
        } catch (SQLException ex) {
            System.err.println("[DB] Cannot load history: " + ex.getMessage());
        }
        return records;
    }

    public record HistoryRecord(String sender, String receiver, String messageType, String content,
                                String fileName, String filePath, String createdAt) {
    }
}
