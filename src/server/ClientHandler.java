package server;

import common.Protocol;
import server.decorator.LoggingHandler;
import server.decorator.SocketHandler;
import server.decorator.ValidationHandler;
import server.observer.ServerBroadcaster;
import server.observer.ServerObserver;
import server.router.MessageRouter;
import server.service.client.IClientManager;
import server.service.file.IFileService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class ClientHandler implements Runnable, SocketHandler, ServerObserver {
    // Mỗi ClientHandler xử lý một kết nối socket của một client.
    private final Socket socket;
    private final IClientManager clientManager;
    private final MessageRouter router;
    private final IFileService fileService;
    private final ServerBroadcaster broadcaster;
    private final InputStream inputStream;
    private final OutputStream outputStream;
    private final SocketHandler baseSender;
    private SocketHandler sendHandler;
    private volatile String username;
    private volatile boolean running = true;

    public ClientHandler(Socket socket, IClientManager clientManager, MessageRouter router, IFileService fileService, ServerBroadcaster broadcaster) throws IOException {
        this.socket = socket;
        this.clientManager = clientManager;
        this.router = router;
        this.fileService = fileService;
        this.broadcaster = broadcaster;
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
        // Vòng lặp chính của thread client: đăng nhập trước, rồi đọc command từ client liên tục.
        try {
            if (!login()) {
                return;
            }
            while (running) {
                String line = Protocol.readLine(inputStream);
                Protocol.ParsedMessage message = Protocol.parse(line);
                if (Protocol.FILE_META.equals(message.getCommand())) {
                    handleFileMeta(message);
                } else if (Protocol.FILE_DOWNLOAD.equals(message.getCommand())) {
                    handleFileDownload(message);
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

    public InputStream getInputStream() {
        return inputStream;
    }

    public OutputStream getOutputStream() {
        return outputStream;
    }

    public IFileService getFileService() {
        return fileService;
    }

    // Xử lý login đầu tiên: client phải gửi LOGIN|username ngay khi kết nối.
    private boolean login() throws IOException {
        String line = Protocol.readLine(inputStream);
        Protocol.ParsedMessage message = Protocol.parse(line);
        if (!Protocol.LOGIN.equals(message.getCommand())) {
            send(Protocol.build(Protocol.ERROR, "First command must be LOGIN"));
            return false;
        }
        String requestedUsername = message.field(0).trim();
        if (!isValidUsername(requestedUsername)) {
            send(Protocol.build(Protocol.ERROR, "Username must be 3-20 letters, numbers, underscore or dash"));
            return false;
        }
        if (!clientManager.addClient(requestedUsername, this)) {
            send(Protocol.build(Protocol.ERROR, "Username already exists"));
            return false;
        }
        username = requestedUsername;
        this.sendHandler = new ValidationHandler(new LoggingHandler(baseSender, username));
        broadcaster.subscribe(this);
        router.sendInitialState(this);
        return true;
    }

    // Xử lý metadata file khi client gửi file lên server.
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

    private boolean isValidUsername(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]{3,20}");
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
