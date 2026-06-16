package com.chatapp.client;

import java.io.IOException;
import java.util.Scanner;

public class MainTestClient {
    public static void main(String[] args) {
        Client client = new Client();

        try {
            client.connect("localhost", 9999, "Hieu");

            Scanner scanner = new Scanner(System.in);

            while (true) {
                String message = scanner.nextLine();

                if (message.equalsIgnoreCase("/exit")) {
                    client.disconnect();
                    break;
                }

                client.sendMessage(message);
            }

        } catch (IOException e) {
            System.out.println("Không thể kết nối tới server: " + e.getMessage());
        }
    }
}
