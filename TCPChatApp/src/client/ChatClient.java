package client;

import client.gui.AppTheme;
import javax.swing.SwingUtilities;

public class ChatClient {
    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 5001;

    public static void main(String[] args) {
        AppTheme.install();
        final String host = args.length >= 1 ? args[0] : DEFAULT_HOST;
        final int port = args.length >= 2 ? parsePort(args[1]) : DEFAULT_PORT;

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new LoginFrame(host, port).setVisible(true);
            }
        });
    }

    private static int parsePort(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return DEFAULT_PORT;
        }
    }
}
