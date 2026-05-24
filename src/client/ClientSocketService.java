package client;

import common.Protocol;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class ClientSocketService {
    public interface Listener {
        void onMessage(Protocol.ParsedMessage message);

        void onFileDownloaded(File file);

        void onDisconnected(String reason);

        void onConnectionStatus(String status);
    }

    private final String host;
    private final int port;
    private Socket socket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private volatile boolean running;
    private volatile boolean reconnecting;
    private String username;
    private Listener listener;

    public ClientSocketService(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void connect(String username, Listener listener) throws IOException {
        this.username = username;
        this.listener = listener;
        this.running = true;
        openSocketAndLogin();
        startReaderThread();
    }

    private synchronized void openSocketAndLogin() throws IOException {
        closeSocketOnly();
        socket = new Socket(host, port);
        inputStream = socket.getInputStream();
        outputStream = socket.getOutputStream();
        send(Protocol.build(Protocol.LOGIN, username));
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
                openSocketAndLogin();
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
}
