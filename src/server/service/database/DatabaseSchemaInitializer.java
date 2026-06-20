package server.service.database;

import common.ConversationType;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Owns schema creation and migration for the chat database.
 */
public class DatabaseSchemaInitializer {
    public void initialize(Connection connection) throws SQLException {
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
        try (Statement statement = connection.createStatement()) {
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
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS users (" +
                    "id BIGSERIAL PRIMARY KEY," +
                    "username TEXT NOT NULL UNIQUE," +
                    "display_name TEXT NOT NULL," +
                    "avatar_path TEXT," +
                    "is_active BOOLEAN NOT NULL DEFAULT TRUE," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "last_login_at TIMESTAMP" +
                    ")");
            statement.executeUpdate("ALTER TABLE users DROP COLUMN IF EXISTS password_hash");
            statement.executeUpdate("ALTER TABLE users DROP COLUMN IF EXISTS password_salt");
        }
    }
}
