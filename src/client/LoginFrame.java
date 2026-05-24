package client;

import common.Protocol;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.io.IOException;

public class LoginFrame extends JFrame {
    private final String host;
    private final int port;
    private final JTextField usernameField = new JTextField();
    private final JLabel statusLabel = new JLabel("Server: ");

    public LoginFrame(String host, int port) {
        this.host = host;
        this.port = port;
        setTitle("TCPChatGUI - Login");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(360, 170);
        setLocationRelativeTo(null);
        initUi();
    }

    private void initUi() {
        JPanel formPanel = new JPanel(new GridLayout(3, 1, 8, 8));
        formPanel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        statusLabel.setText("Server: " + host + ":" + port);
        JButton loginButton = new JButton("Login");
        formPanel.add(statusLabel);
        formPanel.add(usernameField);
        formPanel.add(loginButton);
        add(formPanel, BorderLayout.CENTER);

        getRootPane().setDefaultButton(loginButton);
        loginButton.addActionListener(e -> login());
    }

    private void login() {
        String username = usernameField.getText().trim();
        if (username.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter username");
            return;
        }

        ClientSocketService service = new ClientSocketService(host, port);
        try {
            ChatFrame chatFrame = new ChatFrame(username, service);
            service.connect(username, chatFrame);
            chatFrame.setVisible(true);
            dispose();
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                    "Cannot connect to server: " + e.getMessage(),
                    "Connection error",
                    JOptionPane.ERROR_MESSAGE));
        }
    }
}
