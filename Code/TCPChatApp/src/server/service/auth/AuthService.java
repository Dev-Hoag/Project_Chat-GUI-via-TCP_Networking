package server.service.auth;

import server.service.user.IUserRepository;
import server.service.user.IUserRepository.UserRecord;

public class AuthService {
    private final IUserRepository userRepository;

    public AuthService(IUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserRecord register(String username, String displayName, String avatarPath) throws AuthException {
        validateUsername(username);
        String finalDisplayName = normalizeDisplayName(username, displayName);
        if (!userRepository.isConfigured()) {
            throw new AuthException("User database is not configured");
        }
        if (userRepository.usernameExists(username)) {
            throw new AuthException("Username already exists");
        }
        UserRecord created = userRepository.createUser(username, finalDisplayName, avatarPath);
        userRepository.updateLastLogin(username);
        return created;
    }

    public UserRecord login(String username) throws AuthException {
        validateUsername(username);
        if (!userRepository.isConfigured()) {
            throw new AuthException("User database is not configured");
        }
        UserRecord user = userRepository.getUserByUsername(username);
        if (user == null || !user.active) {
            throw new AuthException("Username is not registered");
        }
        userRepository.updateLastLogin(username);
        return userRepository.getUserByUsername(username);
    }

    private void validateUsername(String username) throws AuthException {
        if (username == null || !username.trim().matches("[A-Za-z0-9_-]{3,20}")) {
            throw new AuthException("Username must be 3-20 letters, numbers, underscore or dash");
        }
    }

    private String normalizeDisplayName(String username, String displayName) {
        if (displayName == null || displayName.trim().isEmpty()) {
            return username;
        }
        return displayName.trim();
    }
}
