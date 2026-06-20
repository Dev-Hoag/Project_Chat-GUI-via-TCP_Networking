package client;

import common.Protocol;

import java.io.*;
import java.net.Socket;

public final class ClientSocketService {
    public static class AuthRequest {
        public final String command;
        public final String username;
        public final String displayName;

        private AuthRequest(String command, String username, String displayName) {
            this.command = command;
            this.username = username;
            this.displayName = displayName;
        }

        public static AuthRequest login(String username) {
            return new AuthRequest(Protocol.LOGIN, username, "");
        }

        public static AuthRequest register(String username, String displayName) {
            return new AuthRequest(Protocol.REGISTER, username, displayName);
        }
    }

    public interface Listener {
        void onMessage(Protocol.ParsedMessage message);

        void onFileDownloaded(File file);

        void onAvatarDownloaded(String avatarPath, File file);

        void onDisconnected(String reason);

        void onConnectionStatus(String status);

        void onTypingStatusChanged(Protocol.ParsedMessage message);
    }

    private final String host;
    private final int port;
    private Socket socket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private volatile boolean running;
    private volatile boolean reconnecting;
    private AuthRequest authRequest;
    private AuthRequest reconnectAuthRequest;
    private Listener listener;

    public ClientSocketService(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void connect(AuthRequest authRequest, Listener listener) throws IOException {
        this.authRequest = authRequest;
        this.reconnectAuthRequest = AuthRequest.login(authRequest.username);
        this.listener = listener;
        this.running = true;
        openSocketAndAuth(authRequest);
        startReaderThread();
    }

    private synchronized void openSocketAndAuth(AuthRequest request) throws IOException {
        closeSocketOnly();
        socket = new Socket(host, port);
        inputStream = socket.getInputStream();
        outputStream = socket.getOutputStream();
        send(Protocol.build(request.command, request.username, request.displayName));
    }

    private void startReaderThread() {
        Thread readerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                readLoop();
            }
        }, "client-socket-reader");
        readerThread.start();
    }

    public synchronized void send(String line) throws IOException {
        if (outputStream == null) {
            throw new IOException("Not connected");
        }
        Protocol.writeLine(outputStream, line);
    }

    public synchronized void sendFile(String conversationType, String conversationId, String receiver, File file) throws IOException {
        send(Protocol.build(Protocol.FILE_META,
                conversationType,
                conversationId,
                receiver == null ? "" : receiver,
                file.getName(),
                String.valueOf(file.length())));
        byte[] buffer = new byte[8192];
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            int read;
            while ((read = fileInputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            outputStream.flush();
        }
    }

    public synchronized void sendAvatar(File file) throws IOException {
        if (file.length() > Protocol.MAX_AVATAR_SIZE_BYTES) {
            throw new IOException("Avatar must be smaller than 2MB");
        }
        send(Protocol.build(Protocol.AVATAR_SET, file.getName(), String.valueOf(file.length())));
        byte[] buffer = new byte[8192];
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            int read;
            while ((read = fileInputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            outputStream.flush();
        }
    }

    public void requestAvatar(String avatarPath) throws IOException {
        if (avatarPath == null || avatarPath.trim().isEmpty()) {
            return;
        }
        send(Protocol.build(Protocol.AVATAR_REQUEST, avatarPath));
    }

    public synchronized void sendTypingStart(String conversationType, String conversationId, String receiver) throws IOException {
        send(Protocol.build(Protocol.TYPING_START, conversationType, conversationId, receiver == null ? "" : receiver));
    }

    public synchronized void sendTypingStop(String conversationType, String conversationId, String receiver) throws IOException {
        send(Protocol.build(Protocol.TYPING_STOP, conversationType, conversationId, receiver == null ? "" : receiver));
    }

    public void requestFileDownload(String fileName, String filePath) throws IOException {
        send(Protocol.build(Protocol.FILE_DOWNLOAD, fileName, filePath));
    }

    public void close() {
        running = false;
        closeSocketOnly();
    }

    private void readLoop() {
        try {
            while (running) {
                String line = Protocol.readLine(inputStream);
                Protocol.ParsedMessage message = Protocol.parse(line);
                if (Protocol.FILE_DOWNLOAD_META.equals(message.getCommand())) {
                    File file = receiveDownload(message.field(0), Long.parseLong(message.field(1)));
                    listener.onFileDownloaded(file);
                } else if (Protocol.AVATAR_DELIVER.equals(message.getCommand())) {
                    File file = receiveAvatar(message.field(0), message.field(1), Long.parseLong(message.field(2)));
                    listener.onAvatarDownloaded(message.field(0), file);
                } else if (Protocol.TYPING_STATUS.equals(message.getCommand())) {
                    listener.onTypingStatusChanged(message);
                } else {
                    listener.onMessage(message);
                }
            }
        } catch (IOException e) {
            if (running) {
                listener.onDisconnected(e.getMessage());
                startReconnectLoop();
            }
        } finally {
            closeSocketOnly();
        }
    }

    private void startReconnectLoop() {
        if (reconnecting || !running) {
            return;
        }
        reconnecting = true;
        Thread reconnectThread = new Thread(new Runnable() {
            @Override
            public void run() {
                reconnectLoop();
            }
        }, "client-reconnect");
        reconnectThread.start();
    }

    private void reconnectLoop() {
        int attempt = 1;
        while (running) {
            listener.onConnectionStatus("Reconnecting... attempt " + attempt);
            try {
                Thread.sleep(3000);
                openSocketAndAuth(reconnectAuthRequest);
                reconnecting = false;
                listener.onConnectionStatus("Reconnected");
                startReaderThread();
                return;
            } catch (Exception e) {
                closeSocketOnly();
                attempt++;
            }
        }
        reconnecting = false;
    }

    private synchronized void closeSocketOnly() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
        socket = null;
        inputStream = null;
        outputStream = null;
    }

    private File receiveDownload(String fileName, long fileSize) throws IOException {
        File downloadsDir = new File("downloads");
        if (!downloadsDir.exists() && !downloadsDir.mkdirs()) {
            throw new IOException("Cannot create downloads directory");
        }
        File outputFile = uniqueFile(downloadsDir, sanitizeFileName(fileName));
        long remaining = fileSize;
        byte[] buffer = new byte[8192];
        try (FileOutputStream fileOutputStream = new FileOutputStream(outputFile)) {
            while (remaining > 0) {
                int read = inputStream.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read == -1) {
                    throw new IOException("Connection closed while downloading file");
                }
                fileOutputStream.write(buffer, 0, read);
                remaining -= read;
            }
        }
        return outputFile;
    }

    private File receiveAvatar(String avatarPath, String fileName, long fileSize) throws IOException {
        File avatarsDir = new File("client_files/avatars");
        if (!avatarsDir.exists() && !avatarsDir.mkdirs()) {
            throw new IOException("Cannot create avatar cache directory");
        }
        File outputFile = uniqueFile(avatarsDir, avatarCacheName(avatarPath, fileName));
        long remaining = fileSize;
        byte[] buffer = new byte[8192];
        try (FileOutputStream fileOutputStream = new FileOutputStream(outputFile)) {
            while (remaining > 0) {
                int read = inputStream.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read == -1) {
                    throw new IOException("Connection closed while downloading avatar");
                }
                fileOutputStream.write(buffer, 0, read);
                remaining -= read;
            }
        }
        return outputFile;
    }

    private File uniqueFile(File dir, String fileName) {
        File file = new File(dir, fileName);
        if (!file.exists()) {
            return file;
        }
        int dotIndex = fileName.lastIndexOf('.');
        String baseName = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
        String extension = dotIndex > 0 ? fileName.substring(dotIndex) : "";
        int index = 1;
        while (true) {
            File candidate = new File(dir, baseName + "_" + index + extension);
            if (!candidate.exists()) {
                return candidate;
            }
            index++;
        }
    }

    private String sanitizeFileName(String value) {
        String cleaned = value == null ? "file" : value.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return cleaned.isEmpty() ? "file" : cleaned;
    }

    private String avatarCacheName(String avatarPath, String fileName) {
        String safeKey = Integer.toHexString((avatarPath == null ? "" : avatarPath).hashCode());
        return safeKey + "_" + sanitizeFileName(fileName);
    }
}
