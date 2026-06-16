package client;

import common.Protocol;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.ButtonGroup;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;

public class LoginFrame extends JFrame {
    private final String host;
    private final int port;
    private final JTextField usernameField = new JTextField();
    private final JTextField displayNameField = new JTextField();
    private final JLabel statusLabel = new JLabel("Server: ");
    private final JLabel avatarLabel = new JLabel("Avatar: none");
    private final JRadioButton loginMode = new JRadioButton("Login", true);
    private final JRadioButton registerMode = new JRadioButton("Register");
    private File selectedAvatar;

    public LoginFrame(String host, int port) {
        this.host = host;
        this.port = port;
        setTitle("TCPChatGUI - Login");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(420, 260);
        setLocationRelativeTo(null);
        initUi();
    }

    private void initUi() {
        JPanel formPanel = new JPanel(new GridLayout(11, 1, 8, 8));
        formPanel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        statusLabel.setText("Server: " + host + ":" + port);

        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(loginMode);
        modeGroup.add(registerMode);

        JButton chooseAvatarButton = new JButton("Choose Avatar");
        JButton submitButton = new JButton("Continue");

        formPanel.add(statusLabel);
        formPanel.add(new JLabel("Username"));
        formPanel.add(usernameField);
        formPanel.add(new JLabel("Display name (register only)"));
        formPanel.add(displayNameField);
        formPanel.add(avatarLabel);
        formPanel.add(loginMode);
        formPanel.add(registerMode);

        JPanel buttonPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
        buttonPanel.add(chooseAvatarButton);
        buttonPanel.add(submitButton);

        JPanel wrapper = new JPanel(new BorderLayout(8, 8));
        wrapper.add(formPanel, BorderLayout.CENTER);
        wrapper.add(buttonPanel, BorderLayout.SOUTH);
        add(wrapper, BorderLayout.CENTER);

        displayNameField.setEnabled(false);
        registerMode.addActionListener(e -> displayNameField.setEnabled(true));
        loginMode.addActionListener(e -> displayNameField.setEnabled(false));
        chooseAvatarButton.addActionListener(e -> chooseAvatar());
        submitButton.addActionListener(e -> continueAuth());
        getRootPane().setDefaultButton(submitButton);
    }

    private void chooseAvatar() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            selectedAvatar = chooser.getSelectedFile();
            avatarLabel.setText("Avatar: " + selectedAvatar.getName());
        }
    }

    private void continueAuth() {
        String username = usernameField.getText().trim();
        String displayName = displayNameField.getText().trim();

        if (username.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter username");
            return;
        }
        if (registerMode.isSelected() && displayName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter display name");
            return;
        }

        ClientSocketService service = new ClientSocketService(host, port);
        ClientSocketService.AuthRequest authRequest = registerMode.isSelected()
                ? ClientSocketService.AuthRequest.register(username, displayName)
                : ClientSocketService.AuthRequest.login(username);

        try {
            ChatFrame chatFrame = new ChatFrame(username, service);
            service.connect(authRequest, chatFrame);
            if (selectedAvatar != null) {
                service.sendAvatar(selectedAvatar);
            }
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
