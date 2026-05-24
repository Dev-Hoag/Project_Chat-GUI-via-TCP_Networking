package server;

import common.Protocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final ClientManager clientManager;
    private final MessageRouter router;
    private final FileService fileService;
    private final InputStream inputStream;
    private final OutputStream outputStream;
    private volatile String username;
    private volatile boolean running = true;

    public ClientHandler(Socket socket, ClientManager clientManager, MessageRouter router, FileService fileService) throws IOException {
        this.socket = socket;
        this.clientManager = clientManager;
        this.router = router;
        this.fileService = fileService;
        this.inputStream = socket.getInputStream();
        this.outputStream = socket.getOutputStream();
    }

    @Override
    public void run() {
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

    public synchronized void send(String line) {
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

    public FileService getFileService() {
        return fileService;
    }

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

    private boolean isValidUsername(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]{3,20}");
    }

    private void close() {
        running = false;
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
