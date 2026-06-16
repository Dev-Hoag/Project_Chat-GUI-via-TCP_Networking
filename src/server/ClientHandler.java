package server;

import common.Protocol;
import server.decorator.SocketHandler;
import server.decorator.ValidationHandler;
import server.observer.ServerBroadcaster;
import server.observer.ServerObserver;
import server.router.MessageRouter;
import server.service.auth.AuthException;
import server.service.auth.AuthService;
import server.service.client.IClientManager;
import server.service.file.IFileService;
import server.service.profile.ProfileService;
import server.service.user.IUserRepository.UserRecord;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class ClientHandler implements Runnable, SocketHandler, ServerObserver {
    private final Socket socket;
    private final IClientManager clientManager;
    private final MessageRouter router;
    private final IFileService fileService;
    private final ServerBroadcaster broadcaster;
    private final AuthService authService;
    private final ProfileService profileService;
    private final InputStream inputStream;
    private final OutputStream outputStream;
    private final SocketHandler baseSender;
    private SocketHandler sendHandler;
    private volatile String username;
    private volatile String displayName;
    private volatile String avatarPath;
    private volatile boolean running = true;

    public ClientHandler(Socket socket, IClientManager clientManager, MessageRouter router, IFileService fileService,
                         ServerBroadcaster broadcaster, AuthService authService, ProfileService profileService) throws IOException {
        this.socket = socket;
        this.clientManager = clientManager;
        this.router = router;
        this.fileService = fileService;
        this.broadcaster = broadcaster;
        this.authService = authService;
        this.profileService = profileService;
        this.inputStream = socket.getInputStream();
        this.outputStream = socket.getOutputStream();
        this.baseSender = new SocketHandler() {
            @Override
            public void send(String line) {
                sendDirect(line);
            }
        };
    }

    @Override
    public void run() {
        try {
            if (!authenticate()) {
                return;
            }
            while (running) {
                String line = Protocol.readLine(inputStream);
                Protocol.ParsedMessage message = Protocol.parse(line);
                if (Protocol.FILE_META.equals(message.getCommand())) {
                    handleFileMeta(message);
                } else if (Protocol.FILE_DOWNLOAD.equals(message.getCommand())) {
                    handleFileDownload(message);
                } else if (Protocol.AVATAR_SET.equals(message.getCommand())) {
                    profileService.handleAvatarSet(this, message);
                } else if (Protocol.AVATAR_REQUEST.equals(message.getCommand())) {
                    profileService.handleAvatarRequest(this, message);
                } else {
                    router.handle(this, message);
                }
            }
        } catch (IOException e) {
            System.out.println("[CLIENT] Disconnected: " + (username == null ? socket : username));
        } catch (Exception e) {
            System.out.println("[CLIENT] Error: " + e.getMessage());
            send(Protocol.build(Protocol.ERROR, e.getMessage()));
        } finally {
            close();
            router.onClientDisconnected(username);
        }
    }

    @Override
    public synchronized void send(String line) {
        if (sendHandler != null) {
            sendHandler.send(line);
        } else {
            sendDirect(line);
        }
    }

    private synchronized void sendDirect(String line) {
        try {
            Protocol.writeLine(outputStream, line);
        } catch (IOException e) {
            running = false;
        }
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getAvatarPath() {
        return avatarPath;
    }

    public void setAvatarPath(String avatarPath) {
        this.avatarPath = avatarPath;
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public OutputStream getOutputStream() {
        return outputStream;
    }

    public IFileService getFileService() {
        return fileService;
    }

    private boolean authenticate() throws IOException {
        while (running) {
            String line = Protocol.readLine(inputStream);
            Protocol.ParsedMessage message = Protocol.parse(line);
            String command = message.getCommand();
            if (Protocol.LOGIN.equals(command)) {
                if (handleLogin(message)) {
                    return true;
                }
                continue;
            }
            if (Protocol.REGISTER.equals(command)) {
                if (handleRegister(message)) {
                    return true;
                }
                continue;
            }
            send(Protocol.build(Protocol.LOGIN_ERROR, "First command must be LOGIN or REGISTER"));
        }
        return false;
    }

    private boolean handleLogin(Protocol.ParsedMessage message) {
        String requestedUsername = message.field(0).trim();
        try {
            UserRecord user = authService.login(requestedUsername);
            return finalizeAuthentication(user);
        } catch (AuthException e) {
            send(Protocol.build(Protocol.LOGIN_ERROR, e.getMessage()));
            return false;
        }
    }

    private boolean handleRegister(Protocol.ParsedMessage message) {
        String requestedUsername = message.field(0).trim();
        String requestedDisplayName = message.field(1);
        try {
            UserRecord user = authService.register(requestedUsername, requestedDisplayName, null);
            return finalizeAuthentication(user);
        } catch (AuthException e) {
            send(Protocol.build(Protocol.REGISTER_ERROR, e.getMessage()));
            return false;
        }
    }

    private boolean finalizeAuthentication(UserRecord user) {
        if (user == null) {
            send(Protocol.build(Protocol.LOGIN_ERROR, "Authentication failed"));
            return false;
        }
        if (!clientManager.addClient(user.username, this)) {
            send(Protocol.build(Protocol.LOGIN_ERROR, "Username already exists online"));
            return false;
        }
        this.username = user.username;
        this.displayName = user.displayName;
        this.avatarPath = user.avatarPath;
        this.sendHandler = new ValidationHandler(baseSender);
        broadcaster.subscribe(this);
        router.sendInitialState(this);
        return true;
    }

    private void handleFileMeta(Protocol.ParsedMessage message) throws Exception {
        String conversationType = message.field(0);
        String conversationId = message.field(1);
        String receiver = message.field(2);
        String fileName = message.field(3);
        long fileSize = Long.parseLong(message.field(4));
        router.handleFile(this, conversationType, conversationId, receiver, fileName, fileSize);
    }

    private void handleFileDownload(Protocol.ParsedMessage message) throws Exception {
        router.handleFileDownload(this, message.field(0), message.field(1));
    }

    @Override
    public void onUserJoined(String username) {
        send(Protocol.build(Protocol.JOIN, username));
    }

    @Override
    public void onUserLeft(String username) {
        send(Protocol.build(Protocol.LEAVE, username));
    }

    @Override
    public void onUserListUpdated(String csvUsers) {
        send(Protocol.build(Protocol.USER_LIST, csvUsers));
    }

    @Override
    public void onRoomListUpdated(String roomList) {
        send(Protocol.build(Protocol.ROOM_LIST, roomList));
    }

    private void close() {
        running = false;
        if (broadcaster != null && username != null) {
            broadcaster.unsubscribe(this);
        }
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
