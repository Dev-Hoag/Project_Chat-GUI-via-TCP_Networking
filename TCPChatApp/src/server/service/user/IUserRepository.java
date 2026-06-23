package server.service.user;

import java.util.List;

/**
 * Persistence contract for user accounts and profile data.
 */
public interface IUserRepository {
    boolean isConfigured();
    boolean usernameExists(String username);
    UserRecord getUserByUsername(String username);
    UserRecord createUser(String username, String displayName, String avatarPath);
    void updateLastLogin(String username);
    void updateAvatarPath(String username, String avatarPath);
    void updateDisplayName(String username, String displayName);
    List<UserRecord> getAllUsers();

    class UserRecord {
        public final long id;
        public final String username;
        public final String displayName;
        public final String avatarPath;
        public final boolean active;
        public final java.sql.Timestamp createdAt;
        public final java.sql.Timestamp updatedAt;
        public final java.sql.Timestamp lastLoginAt;

        public UserRecord(long id, String username,
                          String displayName, String avatarPath, boolean active,
                          java.sql.Timestamp createdAt, java.sql.Timestamp updatedAt, java.sql.Timestamp lastLoginAt) {
            this.id = id;
            this.username = username;
            this.displayName = displayName;
            this.avatarPath = avatarPath;
            this.active = active;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.lastLoginAt = lastLoginAt;
        }
    }
}
