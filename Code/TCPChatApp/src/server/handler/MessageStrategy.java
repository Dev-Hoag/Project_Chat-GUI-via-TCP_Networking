package server.handler;

import common.Protocol;
import server.ClientHandler;

// Strategy interface - xử lý các loại message khác nhau
public interface MessageStrategy {
    void handle(ClientHandler sender, Protocol.ParsedMessage message);
}
