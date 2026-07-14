package server.service.database;

import common.ConversationType;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Owns schema creation and migration for the chat database.
 */
public class DatabaseSchemaInitializer {
    public void initialize(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS rooms (" +
                    "room_id VARCHAR(100) PRIMARY KEY," +
                    "room_name VARCHAR(255) NOT NULL," +
                    "created_by VARCHAR(100) NOT NULL," +
                    "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS room_members (" +
                    "room_id VARCHAR(100) NOT NULL," +
                    "username VARCHAR(100) NOT NULL," +
                    "PRIMARY KEY (room_id, username)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS private_conversations (" +
                    "conversation_id VARCHAR(150) PRIMARY KEY," +
                    "user_a VARCHAR(100) NOT NULL," +
                    "user_b VARCHAR(100) NOT NULL," +
                    "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS users (" +
                    "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                    "username VARCHAR(100) NOT NULL UNIQUE," +
                    "display_name VARCHAR(150) NOT NULL," +
                    "avatar_path VARCHAR(500)," +
                    "is_active BOOLEAN NOT NULL DEFAULT TRUE," +
                    "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                    "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                    "last_login_at TIMESTAMP NULL DEFAULT NULL" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS messages (" +
                    "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                    "sender VARCHAR(100) NOT NULL," +
                    "conversation_type VARCHAR(32) NOT NULL DEFAULT '" + ConversationType.LOBBY + "'," +
                    "conversation_id VARCHAR(150) NOT NULL DEFAULT 'lobby'," +
                    "receiver VARCHAR(100)," +
                    "message_type VARCHAR(32) NOT NULL," +
                    "content TEXT," +
                    "file_name VARCHAR(255)," +
                    "file_path VARCHAR(500)," +
                    "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            ensureColumnExists(connection, "messages", "conversation_type",
                    "ALTER TABLE messages ADD COLUMN conversation_type VARCHAR(32) NOT NULL DEFAULT '" + ConversationType.LOBBY + "'");
            ensureColumnExists(connection, "messages", "conversation_id",
                    "ALTER TABLE messages ADD COLUMN conversation_id VARCHAR(150) NOT NULL DEFAULT 'lobby'");
            dropColumnIfExists(connection, "users", "password_hash");
            dropColumnIfExists(connection, "users", "password_salt");
        }
    }

    private void ensureColumnExists(Connection connection, String tableName, String columnName, String alterSql) throws SQLException {
        if (!columnExists(connection, tableName, columnName)) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(alterSql);
            }
        }
    }

    private void dropColumnIfExists(Connection connection, String tableName, String columnName) throws SQLException {
        if (columnExists(connection, tableName, columnName)) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE " + tableName + " DROP COLUMN " + columnName);
            }
        }
    }

    private boolean columnExists(Connection connection, String tableName, String columnName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getColumns(connection.getCatalog(), null, tableName, columnName)) {
            return resultSet.next();
        }
    }
}
