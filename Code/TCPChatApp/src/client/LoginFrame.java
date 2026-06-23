package client;

import client.gui.AppTheme;
import client.gui.ChatWindow;
import client.gui.SafeFileChooser;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;

public class LoginFrame extends JFrame {
    private final String host;
    private final int port;
    private final JTextField usernameField = new JTextField(20);
    private final JTextField displayNameField = new JTextField(20);
    private final JLabel statusLabel = new JLabel();
    private final JLabel avatarLabel = new JLabel("Chưa chọn ảnh đại diện");
    private final JRadioButton loginMode = new JRadioButton("Đăng nhập", true);
    private final JRadioButton registerMode = new JRadioButton("Đăng ký");
    private File selectedAvatar;

    public LoginFrame(String host, int port) {
        this.host = host;
        this.port = port;
        setTitle("TCP Chat");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new java.awt.Dimension(440, 520));
        setSize(440, 520);
        setLocationRelativeTo(null);
        initUi();
    }

    private void initUi() {
        AppTheme.styleFrame(getContentPane());
        getContentPane().setLayout(new BorderLayout());

        JPanel header = AppTheme.createHeader("TCP Chat", "Kết nối và trò chuyện qua mạng");
        getContentPane().add(header, BorderLayout.NORTH);

        JPanel card = AppTheme.createCard();
        card.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 10, 0);

        statusLabel.setText("Máy chủ: " + host + ":" + port);
        AppTheme.styleMutedLabel(statusLabel);
        card.add(statusLabel, gbc);

        gbc.gridy++;
        card.add(fieldLabel("Tên đăng nhập"), gbc);
        gbc.gridy++;
        AppTheme.styleTextField(usernameField);
        card.add(usernameField, gbc);

        gbc.gridy++;
        card.add(fieldLabel("Tên hiển thị (khi đăng ký)"), gbc);
        gbc.gridy++;
        AppTheme.styleTextField(displayNameField);
        card.add(displayNameField, gbc);

        gbc.gridy++;
        AppTheme.styleMutedLabel(avatarLabel);
        card.add(avatarLabel, gbc);

        gbc.gridy++;
        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 0));
        modePanel.setOpaque(false);
        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(loginMode);
        modeGroup.add(registerMode);
        modePanel.add(loginMode);
        modePanel.add(registerMode);
        card.add(modePanel, gbc);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setBorder(BorderFactory.createEmptyBorder(20, 24, 24, 24));
        wrapper.add(card, BorderLayout.CENTER);
        getContentPane().add(wrapper, BorderLayout.CENTER);

        JButton chooseAvatarButton = new JButton("Chọn avatar");
        JButton submitButton = new JButton("Tiếp tục");
        AppTheme.styleSecondaryButton(chooseAvatarButton);
        AppTheme.stylePrimaryButton(submitButton);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 24, 20, 24));
        buttonPanel.add(chooseAvatarButton);
        buttonPanel.add(submitButton);
        getContentPane().add(buttonPanel, BorderLayout.SOUTH);

        displayNameField.setEnabled(false);
        registerMode.addActionListener(e -> displayNameField.setEnabled(true));
        loginMode.addActionListener(e -> displayNameField.setEnabled(false));
        chooseAvatarButton.addActionListener(e -> chooseAvatar());
        submitButton.addActionListener(e -> continueAuth());
        getRootPane().setDefaultButton(submitButton);
    }

    private JLabel fieldLabel(String text) {
        JLabel label = new JLabel(text);
        AppTheme.styleFieldLabel(label);
        return label;
    }

    private void chooseAvatar() {
        JFileChooser chooser = SafeFileChooser.create();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            selectedAvatar = chooser.getSelectedFile();
            avatarLabel.setText("Avatar: " + selectedAvatar.getName());
        }
    }

    private void continueAuth() {
        String username = usernameField.getText().trim();
        String displayName = displayNameField.getText().trim();

        if (username.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập tên đăng nhập");
            return;
        }
        if (registerMode.isSelected() && displayName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập tên hiển thị");
            return;
        }

        ClientSocketService service = new ClientSocketService(host, port);
        ClientSocketService.AuthRequest authRequest = registerMode.isSelected()
                ? ClientSocketService.AuthRequest.register(username, displayName)
                : ClientSocketService.AuthRequest.login(username);

        try {
            ChatWindow chatWindow = new ChatWindow(username, service, selectedAvatar);
            service.connect(authRequest, chatWindow);
            chatWindow.setVisible(true);
            dispose();
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                    "Không thể kết nối máy chủ: " + e.getMessage(),
                    "Lỗi kết nối",
                    JOptionPane.ERROR_MESSAGE));
        }
    }
}
