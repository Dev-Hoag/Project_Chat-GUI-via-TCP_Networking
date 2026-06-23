package server.service.profile;

import common.Protocol;
import server.ClientHandler;
import server.router.MessageRouter;
import server.service.avatar.AvatarService;
import server.service.avatar.AvatarService.StoredAvatar;
import server.service.user.IUserRepository;

public class ProfileService {
    private final IUserRepository userRepository;
    private final AvatarService avatarService;
    private final MessageRouter router;

    public ProfileService(IUserRepository userRepository, AvatarService avatarService, MessageRouter router) {
        this.userRepository = userRepository;
        this.avatarService = avatarService;
        this.router = router;
    }

    public void handleAvatarSet(ClientHandler handler, Protocol.ParsedMessage message) throws Exception {
        if (handler.getUsername() == null) {
            handler.send(Protocol.build(Protocol.AVATAR_SET_ERROR, "You must login first"));
            return;
        }
        String fileName = message.field(0);
        long fileSize;
        try {
            fileSize = Long.parseLong(message.field(1));
        } catch (NumberFormatException e) {
            handler.send(Protocol.build(Protocol.AVATAR_SET_ERROR, "Invalid avatar size"));
            return;
        }
        StoredAvatar storedAvatar = avatarService.saveAvatar(handler.getInputStream(), handler.getUsername(), fileName, fileSize);
        userRepository.updateAvatarPath(handler.getUsername(), storedAvatar.path);
        handler.setAvatarPath(storedAvatar.path);
        handler.send(Protocol.build(Protocol.AVATAR_SET_SUCCESS, storedAvatar.path));
        router.broadcastUserList();
    }

    public void handleAvatarRequest(ClientHandler handler, Protocol.ParsedMessage message) throws Exception {
        if (handler.getUsername() == null) {
            handler.send(Protocol.build(Protocol.ERROR, "You must login first"));
            return;
        }
        String avatarPath = message.field(0).trim();
        if (avatarPath.isEmpty()) {
            handler.send(Protocol.build(Protocol.ERROR, "Avatar path is required"));
            return;
        }

        java.io.File avatarFile = avatarService.getAvatarFile(avatarPath);
        synchronized (handler) {
            Protocol.writeLine(handler.getOutputStream(), Protocol.build(
                    Protocol.AVATAR_DELIVER,
                    avatarPath,
                    avatarFile.getName(),
                    String.valueOf(avatarFile.length())));
            avatarService.writeAvatarToOutput(avatarFile, handler.getOutputStream());
        }
    }
}
