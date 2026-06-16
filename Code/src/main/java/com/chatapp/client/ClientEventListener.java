package com.chatapp.client;

public interface ClientEventListener {
    void onMessageReceived(String message);
    void onUserListUpdated(String[] users);
    void onSystemMessage(String message);
    void onError(String error);
}
