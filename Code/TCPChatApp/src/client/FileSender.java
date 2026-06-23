package client;

import common.Protocol;

import java.io.File;
import java.io.IOException;

public final class FileSender {
    private final client.ClientSocketService socketService;

    public FileSender(client.ClientSocketService socketService) {
        this.socketService = socketService;
    }

    public void send(String conversationType, String conversationId, String receiver, File file) throws IOException {
        if (file.length() > Protocol.MAX_FILE_SIZE_BYTES) {
            throw new IOException("File must be smaller than 10MB");
        }
        socketService.sendFile(conversationType, conversationId, receiver, file);
    }
}
