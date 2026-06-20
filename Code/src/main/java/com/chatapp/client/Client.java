package com.chatapp.client;
import com.chatapp.config.AppConfig;

import javax.swing.event.ListDataListener;
import java.io.*;
import java.net.*;

/**
 * Client Core — quản lý vòng đời kết nối TCP tới server.
 *
 * Cách dùng (Thành viên 3 - GUI gọi vào):
 * <pre>
 *   Client client = new Client("Hiếu", guiListener);
 *   client.connect("localhost", 9999);   // gọi từ GUI thread
 *   client.sendMessage("Xin chào!");     // gọi khi người dùng nhấn Gửi
 *   client.disconnect();                 // gọi khi đóng cửa sổ
 * </pre>
 */
public class Client {
    private final String username; // Field tên đăng nhập người dùng
    private final ClientEventListener eventListener; // Field lắng nghe kết nối đến server

    private Socket socket; // Field socker dùng để kết nối đến server
    private BufferedReader in;
    private PrintWriter out;
    private MessageSender sender;
    private Thread thread;

    public Client(String username, ClientEventListener eventListener) {
        this.username;
        this.eventListener;
    }
    public void setEventListener(ClientEventListener eventListener) {
        this.eventListener = eventListener;
    }

    public void connect(String host, int port) throws IOException {
        this.socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), AppConfig.CONNECT_TIMEOUT);
        socket.setSoTimeout(AppConfig.READ_TIMEOUT);
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        this.out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
        this.sender = new Mes
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
