package server.router;

import common.Protocol;
import server.ClientHandler;
import server.command.CommandQueue;
import server.command.RouterCommand;
import server.handler.HandlerFactory;
import server.handler.MessageStrategy;
import server.observer.ServerBroadcaster;
import server.service.chat.ChatService;
import server.service.client.IClientManager;
import server.service.database.IDatabase;
import server.service.file.IFileService;

public class MessageRouter {
    private final HandlerFactory handlerFactory;
    private final CommandQueue commandQueue = new CommandQueue();
    private final ChatService chatService;

    public MessageRouter(IClientManager clientManager, IDatabase databaseManager, ServerBroadcaster broadcaster) {
        this.chatService = new ChatService(clientManager, databaseManager, broadcaster);
        this.handlerFactory = new HandlerFactory(this);
    }

    public void sendInitialState(ClientHandler handler) {
        chatService.sendInitialState(handler);
    }

    public void handle(ClientHandler sender, Protocol.ParsedMessage message) {
        String command = message.getCommand();
        MessageStrategy strategy = handlerFactory.getStrategy(command);
        if (strategy == null) {
            sender.send(Protocol.build(Protocol.ERROR, "Unsupported command: " + command));
            return;
        }
        commandQueue.executeAndQueue(new RouterCommand(command, () -> strategy.handle(sender, message)));
    }

    public void handleFile(ClientHandler sender, String conversationType, String conversationId, String receiver,
                           String fileName, long fileSize) throws Exception {
        chatService.handleFile(sender, sender.getFileService(), conversationType, conversationId, receiver, fileName, fileSize);
    }

    public void handleFileDownload(ClientHandler requester, String fileName, String filePath) {
        chatService.handleFileDownload(requester, requester.getFileService(), fileName, filePath);
    }

    public void onClientDisconnected(String username) {
        chatService.onClientDisconnected(username);
    }

    public void handleRoomMessage(ClientHandler sender, String roomId, String content) {
        chatService.handleRoomMessage(sender, roomId, content);
    }

    public void handlePrivateMessage(ClientHandler sender, String receiver, String content) {
        chatService.handlePrivateMessage(sender, receiver, content);
    }

    public void handleCreateRoom(ClientHandler sender, String roomName, String csvUsers) {
        chatService.handleCreateRoom(sender, roomName, csvUsers);
    }

    public void handleJoinRoom(ClientHandler sender, String roomId) {
        chatService.handleJoinRoom(sender, roomId);
    }

    public void handleLeaveRoom(ClientHandler sender, String roomId) {
        chatService.handleLeaveRoom(sender, roomId);
    }

    public void sendHistory(ClientHandler handler, String conversationType, String conversationId) {
        chatService.sendHistory(handler, conversationType, conversationId);
    }
}
