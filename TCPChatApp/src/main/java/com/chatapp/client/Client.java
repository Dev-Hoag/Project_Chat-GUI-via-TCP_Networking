package com.chatapp.client;
import javax.swing.event.ListDataListener;
import java.io.*;
import java.net.*;

public class Client {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private Thread thread;
    private String username;
    private ClientEventListener eventListener;

    public Client() {
    }
    public void setEventListener(ClientEventListener eventListener) {
        this.eventListener = eventListener;
    }

    public void connect(String host, int port, String username) throws IOException {
        this.username = username;
        this.socket = new Socket(host, port);
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintWriter(socket.getOutputStream(), true);
        out.println(("JOIN|" + this.username));
        startListening();
    }
    public void sendMessage(String message) {
        if(out != null && message != null && !message.trim().isEmpty()) {
            out.println("MSG|" + this.username + "|" + message);
        }
    }
    public void disconnect() {
        try{
            if(out != null) {
                out.println(("DISCONNECT|" + this.username));
            }
            if(socket != null) {
                socket.close();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void startListening() {
        this.thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String message;
                    while ((message = in.readLine()) != null) {
                        System.out.println("Server gửi: " + message);

                        if(eventListener != null) {
                            eventListener.onMessageReceived(message);
                        }
                    }
                } catch(IOException e) {
                    System.out.println("Mất kết nối server");
                }
            }
        });
        thread.start();
    }
}
