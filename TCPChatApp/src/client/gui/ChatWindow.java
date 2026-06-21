package client.gui;

/**
 * Cửa sổ chat chính của client.
 * Triển khai {@link ClientSocketService.Listener} — nhận callback từ socket service
 * và cập nhật UI qua {@link SwingUtilities#invokeLater(Runnable)}.
 */
import client.ClientSocketService;
import client.FileSender;
import common.ConversationType;
import common.MessageType;
import common.Protocol;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.ListSelectionModel;
import javax.swing.ListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.Component;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ChatWindow extends JFrame implements ClientSocketService.Listener {
    private final String username;
    private final ClientSocketService socketService;
    private final FileSender fileSender;

    private final JTextPane chatPane = new JTextPane();
    private final JTextField messageInput = new JTextField();
    private final JButton sendButton = new JButton("Send");
    private final JButton sendFileButton = new JButton("File");
    private final JButton avatarButton = new JButton("Avatar");
    private final JButton emojiButton = new JButton("Emoji");
    private final JButton createRoomButton = new JButton("Create Group");
    private final JCheckBox privateModeCheckBox = new JCheckBox("Private");
    private final JLabel statusLabel = new JLabel("Connecting...");
    private final JLabel typingLabel = new JLabel(" ");
    private final JLabel replyBannerLabel = new JLabel(" ");
    private final JButton clearReplyButton = new JButton("Cancel");
    private final JLabel profileLabel = new JLabel("Profile: ");
    private final JLabel avatarPreviewLabel = new JLabel();
    private final DefaultListModel<UserItem> onlineUsersModel = new DefaultListModel<UserItem>();
    private final JList<UserItem> onlineUserList = new JList<UserItem>(onlineUsersModel);
    private final DefaultListModel<ConversationItem> conversationModel = new DefaultListModel<ConversationItem>();
    private final JList<ConversationItem> conversationList = new JList<ConversationItem>(conversationModel);

    private final Map<String, ConversationItem> conversations = new HashMap<String, ConversationItem>();
    private final Map<String, List<ChatEntry>> entriesByConversation = new HashMap<String, List<ChatEntry>>();
    private final Map<Integer, ChatEntry> renderedLines = new HashMap<Integer, ChatEntry>();
    private final Map<String, ImageIcon> avatarIconCache = new HashMap<String, ImageIcon>();
    private final Map<String, File> avatarFilesByPath = new HashMap<String, File>();
    private final Map<String, Set<String>> typingUsersByConversation = new HashMap<String, Set<String>>();
    private final Set<String> loadedHistory = new HashSet<String>();
    private final Set<String> requestedAvatarPaths = new HashSet<String>();
    private final JPopupMenu messagePopupMenu = new JPopupMenu();
    private final JMenuItem replyMenuItem = new JMenuItem("Reply");
    private final JMenuItem forwardMenuItem = new JMenuItem("Forward");
    private final JMenuItem downloadMenuItem = new JMenuItem("Download");
    private final File avatarCacheDir = new File("client_files/avatars"); // Field này sẽ tạo mới avatar nếu chưa có
    private final Timer typingStopTimer = new Timer(1200, e -> stopTyping(true));

    private boolean readyForHistory;
    private boolean connected;
    private String displayName;
    private String avatarPath;
    private ChatEntry pendingReplyEntry;
    private boolean typingActive;
    private TypingTarget activeTypingTarget;
    private ChatEntry popupEntry;
    private File pendingAvatar;

    public ChatWindow(String username, ClientSocketService socketService) {
        this(username, socketService, null);
    }

    public ChatWindow(String username, ClientSocketService socketService, File pendingAvatar) {
        this.username = username;
        this.socketService = socketService;
        this.pendingAvatar = pendingAvatar;
        this.fileSender = new FileSender(socketService);
        setTitle("TCP Chat — " + username);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(880, 600));
        setSize(980, 680);
        setLocationRelativeTo(null);
        AppTheme.styleFrame(getContentPane());
        initUi();
        addLobbyConversation();
    }

    @Override
    public void onMessage(Protocol.ParsedMessage message) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                handleServerMessage(message);
            }
        });
    }

    @Override
    public void onFileDownloaded(File file) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                addSystemToCurrent("Downloaded file: " + file.getPath());
            }
        });
    }

    @Override
    public void onAvatarDownloaded(String avatarPath, File file) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (avatarPath != null && !avatarPath.trim().isEmpty() && file != null) {
                    avatarFilesByPath.put(avatarPath, file);
                    requestedAvatarPaths.remove(avatarPath);
                    avatarIconCache.keySet().removeIf(key -> key.startsWith(avatarPath + "#"));
                    if (avatarPath.equals(ChatWindow.this.avatarPath)) {
                        updateProfileAvatar(avatarPath);
                    }
                    onlineUserList.repaint();
                    conversationList.repaint();
                    repaint();
                }
            }
        });
    }

    @Override
    public void onDisconnected(String reason) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                connected = false;
                statusLabel.setText("Disconnected: " + reason);
                typingUsersByConversation.clear();
                typingLabel.setText(" ");
                requestedAvatarPaths.clear();
                addSystemToCurrent("Disconnected from server");
            }
        });
    }

    @Override
    public void onConnectionStatus(String status) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                statusLabel.setText(status);
                if ("Reconnected".equals(status)) {
                    loadedHistory.clear();
                    typingUsersByConversation.clear();
                    requestedAvatarPaths.clear();
                    typingLabel.setText(" ");
                }
            }
        });
    }

    @Override
    public void onTypingStatusChanged(Protocol.ParsedMessage message) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                handleTypingStatus(message);
            }
        });
    }

    private void initUi() {
        chatPane.setEditable(false);
        chatPane.setFocusable(false);
        AppTheme.styleChatPane(chatPane);
        chatPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    handleChatMouseEvent(e);
                } else {
                    messageInput.requestFocusInWindow();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                handleChatMouseEvent(e);
            }
        });
        buildMessagePopupMenu();

        onlineUserList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        onlineUserList.setCellRenderer(new UserItemRenderer());
        onlineUserList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openPrivateConversationFromSelectedUser();
                }
            }
        });

        conversationList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        conversationList.setCellRenderer(new ConversationItemRenderer());
        conversationList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                stopTyping(true);
                requestSelectedConversationHistory();
                renderSelectedConversation();
                refreshTypingIndicator();
            }
        });

        JPanel leftPanel = AppTheme.createSidebar("Hội thoại");
        leftPanel.add(AppTheme.wrapList(conversationList), BorderLayout.CENTER);
        leftPanel.setPreferredSize(new Dimension(200, 400));

        JPanel rightPanel = AppTheme.createSidebar("Đang online");
        rightPanel.add(AppTheme.wrapList(onlineUserList), BorderLayout.CENTER);
        AppTheme.stylePrimaryButton(createRoomButton);
        createRoomButton.setText("+ Tạo nhóm");
        JPanel createRoomWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 8));
        createRoomWrap.setOpaque(false);
        createRoomWrap.add(createRoomButton);
        rightPanel.add(createRoomWrap, BorderLayout.SOUTH);
        rightPanel.setPreferredSize(new Dimension(210, 400));

        JScrollPane chatScroll = new JScrollPane(chatPane);
        chatScroll.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 1, AppTheme.BORDER));
        chatScroll.getViewport().setBackground(AppTheme.CHAT_BG);
        chatScroll.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                messageInput.requestFocusInWindow();
            }
        });

        JSplitPane centerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, chatScroll);
        centerSplit.setResizeWeight(0);
        centerSplit.setBorder(BorderFactory.createEmptyBorder());
        centerSplit.setDividerSize(1);
        centerSplit.setBackground(AppTheme.BORDER);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, centerSplit, rightPanel);
        mainSplit.setResizeWeight(1);
        mainSplit.setBorder(BorderFactory.createEmptyBorder());
        mainSplit.setDividerSize(1);
        mainSplit.setBackground(AppTheme.BORDER);

        JPanel bottomPanel = new JPanel(new BorderLayout(10, 8));
        bottomPanel.setBackground(AppTheme.SURFACE);
        bottomPanel.setBorder(AppTheme.sectionBorder());

        replyBannerLabel.setForeground(AppTheme.WARNING);
        replyBannerLabel.setFont(AppTheme.smallFont());
        JPanel replyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        replyPanel.setOpaque(false);
        replyPanel.add(replyBannerLabel);
        AppTheme.styleGhostButton(clearReplyButton);
        clearReplyButton.setText("Hủy");
        clearReplyButton.addActionListener(e -> clearReplyTarget());
        replyPanel.add(clearReplyButton);
        bottomPanel.add(replyPanel, BorderLayout.NORTH);

        AppTheme.styleInputField(messageInput);
        messageInput.setToolTipText("Nhập tin nhắn tại đây rồi bấm Gửi hoặc Enter");
        JPanel inputRow = new JPanel(new BorderLayout(8, 0));
        inputRow.setOpaque(false);
        inputRow.add(messageInput, BorderLayout.CENTER);
        bottomPanel.add(inputRow, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttonPanel.setOpaque(false);
        privateModeCheckBox.setOpaque(false);
        privateModeCheckBox.setForeground(AppTheme.TEXT_MUTED);
        privateModeCheckBox.setFont(AppTheme.baseFont());

        AppTheme.styleGhostButton(sendFileButton);
        sendFileButton.setText("📎 Tệp");
        AppTheme.styleGhostButton(emojiButton);
        emojiButton.setText("😀");
        AppTheme.styleGhostButton(avatarButton);
        avatarButton.setText("Ảnh");
        AppTheme.stylePrimaryButton(sendButton);
        sendButton.setText("Gửi");

        buttonPanel.add(privateModeCheckBox);
        buttonPanel.add(sendFileButton);
        buttonPanel.add(emojiButton);
        buttonPanel.add(avatarButton);
        buttonPanel.add(sendButton);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);

        JPanel northPanel = new JPanel(new BorderLayout(16, 0));
        northPanel.setBackground(AppTheme.HEADER);
        northPanel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        JLabel titleLabel = new JLabel("TCP Chat");
        titleLabel.setFont(AppTheme.titleFont().deriveFont(16f));
        titleLabel.setForeground(Color.WHITE);
        northPanel.add(titleLabel, BorderLayout.WEST);

        JPanel statusColumn = new JPanel();
        statusColumn.setLayout(new BoxLayout(statusColumn, BoxLayout.Y_AXIS));
        statusColumn.setOpaque(false);
        AppTheme.styleStatusBar(statusLabel);
        AppTheme.styleTypingLabel(typingLabel);
        statusColumn.add(statusLabel);
        statusColumn.add(typingLabel);
        northPanel.add(statusColumn, BorderLayout.CENTER);

        JPanel profilePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        profilePanel.setOpaque(false);
        AppTheme.styleAvatarPreview(avatarPreviewLabel, 40);
        AppTheme.styleProfileLabel(profileLabel);
        profilePanel.add(profileLabel);
        profilePanel.add(avatarPreviewLabel);
        northPanel.add(profilePanel, BorderLayout.EAST);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().setBackground(AppTheme.BACKGROUND);
        getContentPane().add(northPanel, BorderLayout.NORTH);
        getContentPane().add(mainSplit, BorderLayout.CENTER);
        getContentPane().add(bottomPanel, BorderLayout.SOUTH);

        sendButton.addActionListener(e -> sendText());
        sendFileButton.addActionListener(e -> chooseAndSendFile());
        avatarButton.addActionListener(e -> chooseAndSendAvatar());
        emojiButton.addActionListener(e -> insertEmoji());
        createRoomButton.addActionListener(e -> createRoom());
        messageInput.addActionListener(e -> sendText());
        messageInput.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                handleTypingDraftChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                handleTypingDraftChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                handleTypingDraftChanged();
            }
        });

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(java.awt.event.WindowEvent e) {
                messageInput.requestFocusInWindow();
            }

            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                stopTyping(true);
                socketService.close();
            }
        });

        updateReplyBanner();
        SwingUtilities.invokeLater(() -> messageInput.requestFocusInWindow());
    }

    private void handleServerMessage(Protocol.ParsedMessage message) {
        String command = message.getCommand();
        if (Protocol.LOGIN_SUCCESS.equals(command)) {
            displayName = message.field(1).trim().isEmpty() ? message.field(0) : message.field(1);
            avatarPath = message.field(2);
            statusLabel.setText("Logged in as " + displayName + " | room: lobby");
            profileLabel.setText(displayName + (avatarPath.trim().isEmpty() ? "" : "  •  avatar"));
            updateProfileAvatar(avatarPath);
            readyForHistory = true;
            connected = true;
            typingUsersByConversation.clear();
            typingLabel.setText(" ");
            requestSelectedConversationHistory();
            uploadPendingAvatar();
        } else if (Protocol.LOGIN_ERROR.equals(command) || Protocol.REGISTER_ERROR.equals(command)) {
            connected = false;
            statusLabel.setText("Lỗi đăng nhập: " + message.field(0));
            addSystemToCurrent("Lỗi đăng nhập: " + message.field(0));

            socketService.close();

            JOptionPane.showMessageDialog(this,
                    "Đăng nhập thất bại: " + message.field(0),
                    "Lỗi đăng nhập",
                    JOptionPane.ERROR_MESSAGE);

            dispose();
        } else if (Protocol.ERROR.equals(command)) {
            addSystemToCurrent("Error: " + message.field(0));
        } else if (Protocol.REGISTER_SUCCESS.equals(command)) {
            addSystemToCurrent("Registered successfully as " + message.field(0));
        } else if (Protocol.USER_LIST.equals(command)) {
            updateUsers(message.field(0));
        } else if (Protocol.JOIN.equals(command)) {
            addEntry(Protocol.LOBBY_ROOM_ID, ChatEntry.system("[System] " + message.field(0) + " joined the chat"));
        } else if (Protocol.LEAVE.equals(command)) {
            addEntry(Protocol.LOBBY_ROOM_ID, ChatEntry.system("[System] " + message.field(0) + " left the chat"));
        } else if (Protocol.ROOM_LIST.equals(command)) {
            updateRooms(message.field(0));
        } else if (Protocol.PRIVATE_LIST.equals(command)) {
            updatePrivateConversations(message.field(0));
        } else if (Protocol.ROOM_INVITE.equals(command)) {
            addConversation(new ConversationItem(ConversationType.ROOM, message.field(0), message.field(1), null));
            addSystemToCurrent("Added to room " + message.field(1) + " by " + message.field(2));
        } else if (Protocol.CREATE_ROOM_SUCCESS.equals(command)) {
            addConversation(new ConversationItem(ConversationType.ROOM, message.field(0), message.field(1), null));
            addSystemToCurrent("Created room " + message.field(1));
        } else if (Protocol.ROOM_MSG_DELIVER.equals(command)) {
            addEntry(message.field(0), ChatEntry.text(
                    message.field(0),
                    message.field(1),
                    message.field(2),
                    message.field(2),
                    buildMessageDisplayText(
                            message.field(3) + " " + message.field(1) + ": ",
                            message.field(2),
                            replySender(message),
                            replyMessageType(message),
                            replyContent(message),
                            replyFileName(message))));
        } else if (Protocol.ROOM_MSG_SENT.equals(command)) {
            addEntry(message.field(0), ChatEntry.text(
                    message.field(0),
                    username,
                    message.field(1),
                    message.field(2),
                    buildMessageDisplayText(
                            message.field(2) + " Me: ",
                            message.field(1),
                            replySender(message),
                            replyMessageType(message),
                            replyContent(message),
                            replyFileName(message))));
        } else if (Protocol.PRIVATE_MSG_DELIVER.equals(command)) {
            String sender = message.field(0);
            String conversationId = Protocol.privateConversationId(username, sender);
            addPrivateConversation(sender, false);
            addEntry(conversationId, ChatEntry.text(
                    ConversationType.PRIVATE,
                    conversationId,
                    sender,
                    message.field(1),
                    buildMessageDisplayText(
                            message.field(2) + " " + sender + ": ",
                            message.field(1),
                            replySender(message),
                            replyMessageType(message),
                            replyContent(message),
                            replyFileName(message))));
        } else if (Protocol.PRIVATE_MSG_SENT.equals(command)) {
            String receiver = message.field(0);
            String conversationId = Protocol.privateConversationId(username, receiver);
            addPrivateConversation(receiver, false);
            addEntry(conversationId, ChatEntry.text(
                    ConversationType.PRIVATE,
                    conversationId,
                    username,
                    message.field(1),
                    buildMessageDisplayText(
                            message.field(2) + " Me: ",
                            message.field(1),
                            replySender(message),
                            replyMessageType(message),
                            replyContent(message),
                            replyFileName(message))));
        } else if (Protocol.HISTORY.equals(command)) {
            appendHistory(message);
        } else if (Protocol.FILE_DELIVER.equals(command)) {
            handleFileDeliver(message);
        } else if (Protocol.ROOM_USERS.equals(command)) {
            addSystemToCurrent("Room " + message.field(0) + " users: " + message.field(1));
        } else if (Protocol.AVATAR_SET_SUCCESS.equals(command)) {
            avatarPath = message.field(0);
            profileLabel.setText(displayName + (avatarPath.trim().isEmpty() ? "" : "  •  avatar"));
            updateProfileAvatar(avatarPath);
            addSystemToCurrent("Avatar updated");
        } else if (Protocol.AVATAR_SET_ERROR.equals(command)) {
            addSystemToCurrent("Avatar error: " + message.field(0));
        }
    }

    private void sendText() {
        String text = messageInput.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        if (!connected) {
            addSystemToCurrent("Chưa kết nối máy chủ. Kiểm tra server và đăng nhập lại.");
            return;
        }
        try {
            ConversationItem conversation = getSelectedConversation();
            ChatEntry replyEntry = pendingReplyEntry;
            if (ConversationType.PRIVATE.equals(conversation.type)) {
                socketService.send(buildReplyAwareMessage(Protocol.PRIVATE_MSG, conversation.receiver, text, replyEntry));
            } else if (privateModeCheckBox.isSelected()) {
                String receiver = getSelectedSingleUser();
                if (receiver == null) {
                    addSystemToCurrent("Select one online user for private chat");
                    return;
                }
                addPrivateConversation(receiver, true);
                socketService.send(buildReplyAwareMessage(Protocol.PRIVATE_MSG, receiver, text, replyEntry));
            } else {
                socketService.send(buildReplyAwareMessage(Protocol.ROOM_MSG, conversation.id, text, replyEntry));
            }
            stopTyping(true);
            messageInput.setText("");
            clearReplyTarget();
        } catch (IOException e) {
            addSystemToCurrent("Cannot send message: " + e.getMessage());
        }
    }

    private void chooseAndSendFile() {
        if (!connected) {
            addSystemToCurrent("Chưa kết nối máy chủ. Kiểm tra server và đăng nhập lại.");
            return;
        }
        JFileChooser chooser = SafeFileChooser.create();
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        if (file.length() > Protocol.MAX_FILE_SIZE_BYTES) {
            JOptionPane.showMessageDialog(this, "File must be smaller than 10MB");
            return;
        }
        try {
            ConversationItem conversation = getSelectedConversation();
            if (ConversationType.PRIVATE.equals(conversation.type)) {
                fileSender.send(ConversationType.PRIVATE, conversation.id, conversation.receiver, file);
            } else if (privateModeCheckBox.isSelected()) {
                String receiver = getSelectedSingleUser();
                if (receiver == null) {
                    addSystemToCurrent("Select one online user for private file");
                    return;
                }
                String conversationId = Protocol.privateConversationId(username, receiver);
                addPrivateConversation(receiver, true);
                fileSender.send(ConversationType.PRIVATE, conversationId, receiver, file);
            } else {
                String type = Protocol.LOBBY_ROOM_ID.equals(conversation.id) ? ConversationType.LOBBY : ConversationType.ROOM;
                fileSender.send(type, conversation.id, "", file);
            }
        } catch (IOException e) {
            addSystemToCurrent("Cannot send file: " + e.getMessage());
        }
    }

    private void chooseAndSendAvatar() {
        if (!connected) {
            addSystemToCurrent("Chưa kết nối máy chủ. Kiểm tra server và đăng nhập lại.");
            return;
        }
        JFileChooser chooser = SafeFileChooser.create();
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File avatar = chooser.getSelectedFile();
        if (avatar.length() > Protocol.MAX_AVATAR_SIZE_BYTES) {
            JOptionPane.showMessageDialog(this, "Avatar must be smaller than 2MB");
            return;
        }
        try {
            socketService.sendAvatar(avatar);
        } catch (IOException e) {
            addSystemToCurrent("Cannot send avatar: " + e.getMessage());
        }
    }

    private void insertEmoji() {
        String[] emojis = { "😀", "😂", "😊", "👍", "❤️", "🔥" };
        Object selected = JOptionPane.showInputDialog(this, "Choose emoji", "Emoji",
                JOptionPane.PLAIN_MESSAGE, null, emojis, emojis[0]);
        if (selected != null) {
            messageInput.setText(messageInput.getText() + selected.toString());
            messageInput.requestFocusInWindow();
        }
    }

    private void createRoom() {
        List<String> users = getOnlineUsersExceptMe();
        if (users.isEmpty()) {
            addSystemToCurrent("No other online users to create a group");
            return;
        }

        JTextField roomNameField = new JTextField("Group Chat");
        JPanel usersPanel = new JPanel();
        usersPanel.setLayout(new BoxLayout(usersPanel, BoxLayout.Y_AXIS));
        List<JCheckBox> checkBoxes = new ArrayList<JCheckBox>();
        for (String user : users) {
            JCheckBox checkBox = new JCheckBox(user);
            checkBoxes.add(checkBox);
            usersPanel.add(checkBox);
        }

        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.add(roomNameField, BorderLayout.NORTH);
        panel.add(new JScrollPane(usersPanel), BorderLayout.CENTER);
        panel.setPreferredSize(new Dimension(260, 240));

        int result = JOptionPane.showConfirmDialog(this, panel, "Create Group", JOptionPane.OK_CANCEL_OPTION);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        StringBuilder builder = new StringBuilder();
        for (JCheckBox checkBox : checkBoxes) {
            if (checkBox.isSelected()) {
                if (builder.length() > 0) {
                    builder.append(',');
                }
                builder.append(checkBox.getText());
            }
        }
        if (builder.length() == 0) {
            addSystemToCurrent("Select at least one user for the group");
            return;
        }

        String roomName = roomNameField.getText().trim();
        if (roomName.isEmpty()) {
            roomName = "Group Chat";
        }
        try {
            socketService.send(Protocol.build(Protocol.CREATE_ROOM, roomName, builder.toString()));
        } catch (IOException e) {
            addSystemToCurrent("Cannot create room: " + e.getMessage());
        }
    }

    private List<String> getOnlineUsersExceptMe() {
        List<String> users = new ArrayList<String>();
        for (int i = 0; i < onlineUsersModel.size(); i++) {
            UserItem user = onlineUsersModel.getElementAt(i);
            if (!username.equals(user.username)) {
                users.add(user.username);
            }
        }
        return users;
    }

    private void requestSelectedConversationHistory() {
        if (!readyForHistory) {
            return;
        }
        ConversationItem conversation = conversationList.getSelectedValue();
        if (conversation == null || loadedHistory.contains(conversation.id)) {
            return;
        }
        loadedHistory.add(conversation.id);
        try {
            socketService.send(Protocol.build(Protocol.HISTORY_REQUEST, conversation.type, conversation.id));
        } catch (IOException e) {
            addSystemToCurrent("Cannot load history: " + e.getMessage());
        }
    }

    private void appendHistory(Protocol.ParsedMessage message) {
        String conversationType = message.field(0);
        String conversationId = message.field(1);
        String sender = message.field(2);
        String messageType = message.field(4);
        StoredReply storedReply = decodeStoredReply(message.field(5));
        String content = storedReply == null ? message.field(5) : storedReply.content;
        String fileName = message.field(6);
        String filePath = message.field(7);
        String time = message.field(8);

        if (MessageType.FILE.equals(messageType)) {
            addEntry(conversationId, ChatEntry.file(
                    conversationType,
                    conversationId,
                    sender,
                    "[History] " + time + " " + sender + ": ",
                    fileName,
                    filePath));
        } else {
            addEntry(conversationId, ChatEntry.text(
                    conversationType,
                    conversationId,
                    sender,
                    content,
                    buildMessageDisplayText("[History] " + time + " " + sender + ": ",
                            content,
                            storedReply == null ? "" : storedReply.sender,
                            storedReply == null ? "" : storedReply.messageType,
                            storedReply == null ? "" : storedReply.content,
                            storedReply == null ? "" : storedReply.fileName),
                    storedReply == null ? "" : storedReply.sender,
                    storedReply == null ? "" : storedReply.messageType,
                    storedReply == null ? "" : storedReply.content,
                    storedReply == null ? "" : storedReply.fileName));
        }
    }

    private void handleFileDeliver(Protocol.ParsedMessage message) {
        String conversationType = message.field(0);
        String conversationId = message.field(1);
        String sender = message.field(2);
        String fileName = message.field(3);
        String filePath = message.field(4);
        String time = message.field(5);

        if (ConversationType.PRIVATE.equals(conversationType)) {
            String other = sender.equals(username) ? findPrivateReceiverById(conversationId) : sender;
            addPrivateConversation(other, false);
        }
        addEntry(conversationId, ChatEntry.file(
                conversationType,
                conversationId,
                sender,
                time + " " + sender + " sent file: ",
                fileName,
                filePath));
    }

    private void updateUsers(String csvUsers) {
        onlineUsersModel.clear();
        if (csvUsers.trim().isEmpty()) {
            return;
        }
        for (String rawUser : csvUsers.split(",")) {
            UserItem user = parseUserItem(rawUser);
            if (user != null) {
                onlineUsersModel.addElement(user);
                ensureAvatarRequested(user.avatarPath);
            }
        }
    }

    private void updateRooms(String roomList) {
        if (roomList.trim().isEmpty()) {
            return;
        }
        for (String rawRoom : roomList.split(",")) {
            String[] parts = rawRoom.split(":", 2);
            if (parts.length == 2) {
                String type = Protocol.LOBBY_ROOM_ID.equals(parts[0]) ? ConversationType.LOBBY : ConversationType.ROOM;
                addConversation(new ConversationItem(type, parts[0], parts[1], null));
            }
        }
    }

    private void updatePrivateConversations(String privateList) {
        if (privateList.trim().isEmpty()) {
            return;
        }
        for (String rawPrivate : privateList.split(",")) {
            String[] parts = rawPrivate.split(":", 2);
            if (parts.length == 2) {
                addConversation(new ConversationItem(ConversationType.PRIVATE, parts[0], "Private: " + parts[1], parts[1]));
            }
        }
    }

    private void addLobbyConversation() {
        addConversation(new ConversationItem(ConversationType.LOBBY, Protocol.LOBBY_ROOM_ID, Protocol.LOBBY_ROOM_NAME, null));
        conversationList.setSelectedIndex(0);
    }

    private void addConversation(ConversationItem item) {
        if (item == null || conversations.containsKey(item.id)) {
            return;
        }
        conversations.put(item.id, item);
        entriesByConversation.put(item.id, new ArrayList<ChatEntry>());
        conversationModel.addElement(item);
    }

    private ConversationItem getSelectedConversation() {
        ConversationItem item = conversationList.getSelectedValue();
        if (item == null) {
            item = conversations.get(Protocol.LOBBY_ROOM_ID);
            conversationList.setSelectedValue(item, true);
        }
        return item;
    }

    private String getSelectedSingleUser() {
        List<UserItem> selected = onlineUserList.getSelectedValuesList();
        if (selected.size() != 1) {
            return null;
        }
        UserItem receiver = selected.get(0);
        return receiver.username.equals(username) ? null : receiver.username;
    }

    private void openPrivateConversationFromSelectedUser() {
        String receiver = getSelectedSingleUser();
        if (receiver == null) {
            addSystemToCurrent("Double click exactly one user, not yourself");
            return;
        }
        addPrivateConversation(receiver, true);
    }

    private void addPrivateConversation(String receiver, boolean select) {
        if (receiver == null || receiver.trim().isEmpty() || receiver.equals(username)) {
            return;
        }
        String conversationId = Protocol.privateConversationId(username, receiver);
        addConversation(new ConversationItem(ConversationType.PRIVATE, conversationId, "Private: " + receiver, receiver));
        if (select) {
            conversationList.setSelectedValue(conversations.get(conversationId), true);
            privateModeCheckBox.setSelected(false);
        }
    }

    private String findPrivateReceiverById(String conversationId) {
        ConversationItem item = conversations.get(conversationId);
        if (item != null && item.receiver != null) {
            return item.receiver;
        }
        String prefix = "private_";
        if (conversationId.startsWith(prefix)) {
            String body = conversationId.substring(prefix.length());
            String first = body.substring(0, body.indexOf('_'));
            String second = body.substring(body.indexOf('_') + 1);
            return first.equals(username) ? second : first;
        }
        return null;
    }

    private void addSystemToCurrent(String text) {
        ConversationItem conversation = getSelectedConversation();
        addEntry(conversation.id, ChatEntry.system("[System] " + text));
    }

    private void addEntry(String conversationId, ChatEntry entry) {
        List<ChatEntry> entries = entriesByConversation.get(conversationId);
        if (entries == null) {
            entries = new ArrayList<ChatEntry>();
            entriesByConversation.put(conversationId, entries);
        }
        entries.add(entry);
        ConversationItem selected = conversationList.getSelectedValue();
        if (selected != null && selected.id.equals(conversationId)) {
            renderSelectedConversation();
        }
    }

    private void buildMessagePopupMenu() {
        replyMenuItem.addActionListener(e -> replyToSelectedMessage());
        forwardMenuItem.addActionListener(e -> forwardSelectedMessage());
        downloadMenuItem.addActionListener(e -> downloadSelectedFile());
        messagePopupMenu.add(replyMenuItem);
        messagePopupMenu.add(forwardMenuItem);
        messagePopupMenu.add(downloadMenuItem);
    }

    private void handleChatMouseEvent(MouseEvent e) {
        int line = getLineAtOffset(chatPane.viewToModel(e.getPoint()));
        ChatEntry entry = renderedLines.get(line);
        if (entry == null) {
            return;
        }

        if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
            if (entry.file) {
                downloadSelectedFile(entry);
            }
            return;
        }

        if (e.isPopupTrigger()) {
            chatPane.setCaretPosition(chatPane.viewToModel(e.getPoint()));
            showMessagePopup(e, entry);
        }
    }

    private void showMessagePopup(MouseEvent e, ChatEntry entry) {
        popupEntry = entry;
        replyMenuItem.setEnabled(!entry.system);
        forwardMenuItem.setEnabled(!entry.system);
        downloadMenuItem.setEnabled(entry.file);
        messagePopupMenu.show(chatPane, e.getX(), e.getY());
    }

    private void replyToSelectedMessage() {
        ChatEntry entry = popupEntry != null ? popupEntry : getEntryAtCaret();
        if (entry == null || entry.system) {
            addSystemToCurrent("Select a message to reply");
            return;
        }
        pendingReplyEntry = entry;
        updateReplyBanner();
        messageInput.requestFocusInWindow();
    }

    private void forwardSelectedMessage() {
        ChatEntry entry = popupEntry != null ? popupEntry : getEntryAtCaret();
        if (entry == null) {
            addSystemToCurrent("Select a message to forward");
            return;
        }
        forwardEntry(entry);
    }

    private void downloadSelectedFile() {
        ChatEntry entry = popupEntry != null ? popupEntry : getEntryAtCaret();
        if (entry == null || !entry.file) {
            addSystemToCurrent("Select a file message to download");
            return;
        }
        downloadSelectedFile(entry);
    }

    private void downloadSelectedFile(ChatEntry entry) {
        try {
            socketService.requestFileDownload(entry.fileName, entry.filePath);
        } catch (IOException e) {
            addSystemToCurrent("Cannot download file: " + e.getMessage());
        }
    }

    private ChatEntry getEntryAtCaret() {
        int offset = chatPane.getCaretPosition();
        int line = getLineAtOffset(offset);
        return renderedLines.get(line);
    }

    private void clearReplyTarget() {
        pendingReplyEntry = null;
        updateReplyBanner();
    }

    private void updateReplyBanner() {
        if (pendingReplyEntry == null) {
            replyBannerLabel.setText(" ");
            return;
        }
        replyBannerLabel.setText("Replying to " + summarizeReplyTarget(pendingReplyEntry));
    }

    private String summarizeReplyTarget(ChatEntry entry) {
        if (entry == null) {
            return "";
        }
        if (entry.file) {
            return entry.sender + " | file: " + entry.fileName;
        }
        String text = entry.content == null ? "" : entry.content.trim();
        if (text.length() > 48) {
            text = text.substring(0, 45) + "...";
        }
        return entry.sender + ": " + text;
    }

    private String buildReplyAwareMessage(String command, String target, String text, ChatEntry replyEntry) {
        if (replyEntry == null) {
            return Protocol.build(command, target, text);
        }
        return Protocol.build(command,
                target,
                text,
                replyEntry.sender == null ? "" : replyEntry.sender,
                replyEntry.messageType == null ? "" : replyEntry.messageType,
                replyEntry.content == null ? "" : replyEntry.content,
                replyEntry.fileName == null ? "" : replyEntry.fileName);
    }

    private String replySender(Protocol.ParsedMessage message) {
        return message.getFields().size() > 3 ? message.field(3) : "";
    }

    private String replyMessageType(Protocol.ParsedMessage message) {
        return message.getFields().size() > 4 ? message.field(4) : "";
    }

    private String replyContent(Protocol.ParsedMessage message) {
        return message.getFields().size() > 5 ? message.field(5) : "";
    }

    private String replyFileName(Protocol.ParsedMessage message) {
        return message.getFields().size() > 6 ? message.field(6) : "";
    }

    private StoredReply decodeStoredReply(String value) {
        if (value == null || !value.startsWith(Protocol.REPLY_STORAGE_PREFIX)) {
            return null;
        }
        String payload = value.substring(Protocol.REPLY_STORAGE_PREFIX.length());
        String[] parts = payload.split("\\|", -1);
        if (parts.length < 5) {
            return null;
        }
        return new StoredReply(
                Protocol.decode(parts[0]),
                Protocol.decode(parts[1]),
                Protocol.decode(parts[2]),
                Protocol.decode(parts[3]),
                Protocol.decode(parts[4]));
    }

    private String buildMessageDisplayText(String prefix, String content,
                                           String replySender, String replyMessageType,
                                           String replyContent, String replyFileName) {
        StringBuilder builder = new StringBuilder();
        if (replySender != null && !replySender.trim().isEmpty() && replyMessageType != null && !replyMessageType.trim().isEmpty()) {
            builder.append(buildReplyBlock(new StoredReply(replySender, replyMessageType, replyContent, replyFileName)));
        }
        builder.append(prefix);
        builder.append(content == null ? "" : content);
        return builder.toString();
    }

    private void forwardEntry(ChatEntry entry) {
        if (entry == null || entry.sender == null || "system".equals(entry.sender)) {
            addSystemToCurrent("Cannot forward this message");
            return;
        }
        ForwardTarget target = chooseForwardTarget();
        if (target == null) {
            return;
        }
        try {
            socketService.send(Protocol.build(Protocol.FORWARD_MSG,
                    entry.conversationType,
                    entry.conversationId,
                    entry.messageType,
                    entry.content == null ? "" : entry.content,
                    entry.fileName == null ? "" : entry.fileName,
                    entry.filePath == null ? "" : entry.filePath,
                    target.conversationType,
                    target.conversationId,
                    target.receiver == null ? "" : target.receiver));
            addSystemToCurrent("Forwarded message to " + target.label);
        } catch (IOException e) {
            addSystemToCurrent("Cannot forward message: " + e.getMessage());
        }
    }

    private ForwardTarget chooseForwardTarget() {
        List<ForwardTarget> targets = collectForwardTargets();
        if (targets.isEmpty()) {
            addSystemToCurrent("No target available for forward");
            return null;
        }
        ForwardTarget selected = (ForwardTarget) JOptionPane.showInputDialog(
                this,
                "Choose destination",
                "Forward Message",
                JOptionPane.PLAIN_MESSAGE,
                null,
                targets.toArray(new ForwardTarget[0]),
                targets.get(0));
        return selected;
    }

    private List<ForwardTarget> collectForwardTargets() {
        List<ForwardTarget> targets = new ArrayList<ForwardTarget>();
        for (int i = 0; i < conversationModel.size(); i++) {
            ConversationItem item = conversationModel.getElementAt(i);
            if (item == null || Protocol.LOBBY_ROOM_ID.equals(item.id) && ConversationType.LOBBY.equals(item.type)) {
                targets.add(new ForwardTarget(item.name, item.type, item.id, null));
            } else if (ConversationType.ROOM.equals(item.type)) {
                targets.add(new ForwardTarget(item.name, item.type, item.id, null));
            }
        }
        for (int i = 0; i < onlineUsersModel.size(); i++) {
            UserItem user = onlineUsersModel.getElementAt(i);
            if (user != null && !username.equals(user.username)) {
                targets.add(new ForwardTarget("User: " + user.displayName + " @" + user.username,
                        ConversationType.PRIVATE,
                        Protocol.privateConversationId(username, user.username),
                        user.username));
            }
        }
        return targets;
    }

    private void renderSelectedConversation() {
        ConversationItem conversation = conversationList.getSelectedValue();
        if (conversation == null) {
            return;
        }
        renderedLines.clear();
        StyledDocument document = chatPane.getStyledDocument();
        try {
            document.remove(0, document.getLength());
            List<ChatEntry> entries = entriesByConversation.get(conversation.id);
            if (entries == null) {
                return;
            }
            int line = 0;
            for (ChatEntry entry : entries) {
                renderedLines.put(line, entry);
                if (entry.file) {
                    insert(document, entry.prefix, normalStyle());
                    insert(document, entry.fileName, fileStyle());
                    insert(document, System.lineSeparator(), normalStyle());
                } else if (entry.system) {
                    insert(document, entry.text + System.lineSeparator(), systemStyle());
                } else {
                    if (hasReplyMeta(entry)) {
                        insert(document, buildReplyBlock(entry), replyStyle());
                    }
                    insert(document, entry.text + System.lineSeparator(), normalStyle());
                }
                line++;
            }
            chatPane.setCaretPosition(document.getLength());
        } catch (BadLocationException e) {
            addSystemToCurrent("Cannot render chat: " + e.getMessage());
        }
    }

    private void insert(StyledDocument document, String text, SimpleAttributeSet style) throws BadLocationException {
        document.insertString(document.getLength(), text, style);
    }

    private SimpleAttributeSet normalStyle() {
        SimpleAttributeSet set = new SimpleAttributeSet();
        StyleConstants.setForeground(set, AppTheme.TEXT);
        StyleConstants.setFontSize(set, 13);
        return set;
    }

    private SimpleAttributeSet fileStyle() {
        SimpleAttributeSet set = new SimpleAttributeSet();
        StyleConstants.setForeground(set, AppTheme.FILE_LINK);
        StyleConstants.setUnderline(set, true);
        StyleConstants.setBold(set, true);
        return set;
    }

    private SimpleAttributeSet replyStyle() {
        SimpleAttributeSet set = new SimpleAttributeSet();
        StyleConstants.setForeground(set, AppTheme.REPLY);
        StyleConstants.setItalic(set, true);
        StyleConstants.setLeftIndent(set, 8);
        return set;
    }

    private SimpleAttributeSet systemStyle() {
        SimpleAttributeSet set = new SimpleAttributeSet();
        StyleConstants.setForeground(set, AppTheme.SYSTEM);
        StyleConstants.setItalic(set, true);
        return set;
    }

    private void downloadFileAtClick(MouseEvent event) {
        int offset = chatPane.viewToModel(event.getPoint());
        int line = getLineAtOffset(offset);
        ChatEntry entry = renderedLines.get(line);
        if (entry == null || !entry.file) {
            return;
        }
        try {
            socketService.requestFileDownload(entry.fileName, entry.filePath);
        } catch (IOException e) {
            addSystemToCurrent("Cannot download file: " + e.getMessage());
        }
    }

    private int getLineAtOffset(int offset) {
        Document document = chatPane.getDocument();
        String text;
        try {
            text = document.getText(0, Math.max(0, Math.min(offset, document.getLength())));
        } catch (BadLocationException e) {
            return -1;
        }
        int line = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private UserItem parseUserItem(String rawUser) {
        if (rawUser == null) {
            return null;
        }
        String trimmed = rawUser.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String[] parts = trimmed.split("\\^", 3);
        String user = parts.length > 0 ? parts[0].trim() : "";
        if (user.isEmpty()) {
            return null;
        }
        String display = parts.length > 1 && !parts[1].trim().isEmpty() ? parts[1].trim() : user;
        String avatar = parts.length > 2 ? parts[2].trim() : "";
        return new UserItem(user, display, avatar);
    }

    private void uploadPendingAvatar() {
        if (pendingAvatar == null) {
            return;
        }
        File avatar = pendingAvatar;
        pendingAvatar = null;
        if (avatar.length() > Protocol.MAX_AVATAR_SIZE_BYTES) {
            addSystemToCurrent("Avatar phải nhỏ hơn 2MB");
            return;
        }
        try {
            socketService.sendAvatar(avatar);
        } catch (IOException e) {
            addSystemToCurrent("Không gửi được avatar: " + e.getMessage());
        }
    }

    private void updateProfileAvatar(String path) {
        ensureAvatarRequested(path);
        avatarPreviewLabel.setIcon(loadAvatarIcon(path, 36));
        if (avatarPreviewLabel.getIcon() == null) {
            avatarPreviewLabel.setText(path == null || path.trim().isEmpty() ? "No avatar" : "Loading...");
        } else {
            avatarPreviewLabel.setText("");
        }
    }

    private void handleTypingDraftChanged() {
        if (!connected) {
            return;
        }
        String text = messageInput.getText().trim();
        if (text.isEmpty()) {
            stopTyping(true);
            refreshTypingIndicator();
            return;
        }

        TypingTarget target = resolveTypingTarget();
        if (target == null) {
            stopTyping(true);
            refreshTypingIndicator();
            return;
        }

        if (activeTypingTarget == null || !activeTypingTarget.equals(target)) {
            stopTyping(true);
            startTyping(target);
        } else if (!typingActive) {
            startTyping(target);
        }
        typingStopTimer.restart();
    }

    private void startTyping(TypingTarget target) {
        if (target == null) {
            return;
        }
        try {
            socketService.sendTypingStart(target.conversationType, target.conversationId, target.receiver);
            typingActive = true;
            activeTypingTarget = target;
        } catch (IOException ignored) {
        }
    }

    private void stopTyping(boolean notifyServer) {
        typingStopTimer.stop();
        if (notifyServer && typingActive && activeTypingTarget != null) {
            try {
                socketService.sendTypingStop(activeTypingTarget.conversationType, activeTypingTarget.conversationId, activeTypingTarget.receiver);
            } catch (IOException ignored) {
            }
        }
        typingActive = false;
        activeTypingTarget = null;
    }

    private TypingTarget resolveTypingTarget() {
        ConversationItem conversation = conversationList.getSelectedValue();
        if (conversation != null && ConversationType.PRIVATE.equals(conversation.type)) {
            return new TypingTarget(conversation.type, conversation.id, conversation.receiver);
        }
        if (privateModeCheckBox.isSelected()) {
            String receiver = getSelectedSingleUser();
            if (receiver != null) {
                return new TypingTarget(ConversationType.PRIVATE, Protocol.privateConversationId(username, receiver), receiver);
            }
        }
        if (conversation != null) {
            return new TypingTarget(conversation.type, conversation.id, conversation.receiver);
        }
        return null;
    }

    private void handleTypingStatus(Protocol.ParsedMessage message) {
        String conversationType = message.field(0);
        String conversationId = message.field(1);
        String sender = message.field(2);
        boolean typing = Boolean.parseBoolean(message.field(3));

        if (sender == null || sender.trim().isEmpty() || username.equals(sender)) {
            return;
        }

        String key = conversationKey(conversationType, conversationId);
        Set<String> users = typingUsersByConversation.get(key);
        if (users == null) {
            users = new HashSet<String>();
            typingUsersByConversation.put(key, users);
        }
        if (typing) {
            users.add(sender);
        } else {
            users.remove(sender);
        }
        refreshTypingIndicator();
    }

    private void refreshTypingIndicator() {
        ConversationItem conversation = conversationList.getSelectedValue();
        if (conversation == null) {
            typingLabel.setText(" ");
            return;
        }

        String key = conversationKey(conversation.type, conversation.id);
        Set<String> users = typingUsersByConversation.get(key);
        if (users == null || users.isEmpty()) {
            typingLabel.setText(" ");
            return;
        }

        if (users.size() == 1) {
            typingLabel.setText(users.iterator().next() + " đang soạn...");
        } else {
            List<String> names = new ArrayList<String>(users);
            java.util.Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
            if (names.size() == 2) {
                typingLabel.setText(names.get(0) + " và " + names.get(1) + " đang soạn...");
            } else {
                StringBuilder builder = new StringBuilder();
                builder.append(names.get(0));
                builder.append(", ");
                builder.append(names.get(1));
                if (names.size() == 3) {
                    builder.append(" và ").append(names.get(2));
                } else {
                    builder.append(" và ").append(names.size() - 2).append(" người khác");
                }
                builder.append(" đang soạn...");
                typingLabel.setText(builder.toString());
            }
        }
    }

    private String conversationKey(String conversationType, String conversationId) {
        return conversationType + ":" + conversationId;
    }

    private boolean hasReplyMeta(ChatEntry entry) {
        return entry != null
                && entry.replySender != null && !entry.replySender.trim().isEmpty()
                && entry.replyMessageType != null && !entry.replyMessageType.trim().isEmpty();
    }

    private String buildReplyBlock(ChatEntry entry) {
        StringBuilder builder = new StringBuilder();
        builder.append("↪ Reply to ").append(entry.replySender);
        if (MessageType.FILE.equals(entry.replyMessageType)) {
            builder.append(" [file]");
            if (entry.replyFileName != null && !entry.replyFileName.trim().isEmpty()) {
                builder.append(" ").append(entry.replyFileName);
            }
        } else {
            String preview = entry.replyContent == null ? "" : entry.replyContent.trim();
            if (preview.length() > 60) {
                preview = preview.substring(0, 57) + "...";
            }
            if (!preview.isEmpty()) {
                builder.append(": ").append(preview);
            }
        }
        builder.append(System.lineSeparator());
        return builder.toString();
    }

    private String buildReplyBlock(StoredReply reply) {
        StringBuilder builder = new StringBuilder();
        builder.append("↪ Reply to ").append(reply.sender);
        if (MessageType.FILE.equals(reply.messageType)) {
            builder.append(" [file]");
            if (reply.fileName != null && !reply.fileName.trim().isEmpty()) {
                builder.append(" ").append(reply.fileName);
            }
        } else {
            String preview = reply.content == null ? "" : reply.content.trim();
            if (preview.length() > 60) {
                preview = preview.substring(0, 57) + "...";
            }
            if (!preview.isEmpty()) {
                builder.append(": ").append(preview);
            }
        }
        builder.append(System.lineSeparator());
        return builder.toString();
    }

    private ImageIcon loadAvatarIcon(String path, int size) {
        String normalized = path == null ? "" : path.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        String cacheKey = normalized + "#" + size;
        if (avatarIconCache.containsKey(cacheKey)) {
            return avatarIconCache.get(cacheKey);
        }
        try {
            File file = resolveAvatarCacheFile(normalized);
            if (file == null || !file.exists()) {
                return null;
            }
            BufferedImage image = ImageIO.read(file);
            if (image == null) {
                return null;
            }
            Image scaled = image.getScaledInstance(size, size, Image.SCALE_SMOOTH);
            ImageIcon icon = new ImageIcon(scaled);
            avatarIconCache.put(cacheKey, icon);
            return icon;
        } catch (IOException e) {
            return null;
        }
    }

    private File resolveAvatarCacheFile(String avatarPath) {
        String normalized = avatarPath == null ? "" : avatarPath.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        File mapped = avatarFilesByPath.get(normalized);
        if (mapped != null && mapped.exists()) {
            return mapped;
        }
        String prefix = Integer.toHexString(normalized.hashCode()) + "_";
        File[] matches = avatarCacheDir.listFiles((dir, name) -> name.startsWith(prefix));
        if (matches != null && matches.length > 0) {
            File file = matches[0];
            avatarFilesByPath.put(normalized, file);
            return file;
        }
        return null;
    }

    private void ensureAvatarRequested(String path) {
        String normalized = path == null ? "" : path.trim();
        if (normalized.isEmpty()) {
            return;
        }
        File cachedFile = avatarFilesByPath.get(normalized);
        if (cachedFile != null && cachedFile.exists()) {
            return;
        }
        if (!requestedAvatarPaths.add(normalized)) {
            return;
        }
        try {
            socketService.requestAvatar(normalized);
        } catch (IOException e) {
            requestedAvatarPaths.remove(normalized);
        }
    }

    private static class ConversationItem {
        final String type;
        final String id;
        final String name;
        final String receiver;

        ConversationItem(String type, String id, String name, String receiver) {
            this.type = type;
            this.id = id;
            this.name = name;
            this.receiver = receiver;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private class ConversationItemRenderer extends JLabel implements ListCellRenderer<ConversationItem> {
        @Override
        public Component getListCellRendererComponent(JList<? extends ConversationItem> list, ConversationItem value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            setOpaque(true);
            setFont(AppTheme.baseFont());
            setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
            if (isSelected) {
                setBackground(AppTheme.SELECTION);
                setForeground(AppTheme.PRIMARY);
            } else {
                setBackground(AppTheme.SURFACE);
                setForeground(AppTheme.TEXT);
            }
            if (value == null) {
                setText("");
                return this;
            }
            String icon = ConversationType.PRIVATE.equals(value.type) ? "💬 "
                    : ConversationType.ROOM.equals(value.type) ? "👥 " : "🏠 ";
            setText(icon + value.name);
            return this;
        }
    }

    private class UserItemRenderer extends JLabel implements ListCellRenderer<UserItem> {
        @Override
        public Component getListCellRendererComponent(JList<? extends UserItem> list, UserItem value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            setOpaque(true);
            setFont(AppTheme.baseFont());
            if (isSelected) {
                setBackground(AppTheme.SELECTION);
                setForeground(AppTheme.PRIMARY);
            } else {
                setBackground(AppTheme.SURFACE);
                setForeground(AppTheme.TEXT);
            }
            if (value == null) {
                setText("");
                setIcon(null);
                return this;
            }
            setText(value.displayName);
            setIcon(loadAvatarIcon(value.avatarPath, 30));
            setHorizontalTextPosition(JLabel.RIGHT);
            setIconTextGap(10);
            setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
            return this;
        }
    }

    private static class UserItem {
        final String username;
        final String displayName;
        final String avatarPath;

        UserItem(String username, String displayName, String avatarPath) {
            this.username = username;
            this.displayName = displayName;
            this.avatarPath = avatarPath;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private static class ChatEntry {
        final boolean file;
        final boolean system;
        final String text;
        final String prefix;
        final String fileName;
        final String filePath;
        final String conversationType;
        final String conversationId;
        final String sender;
        final String messageType;
        final String content;
        final String replySender;
        final String replyMessageType;
        final String replyContent;
        final String replyFileName;

        private ChatEntry(boolean file, boolean system, String text, String prefix, String fileName, String filePath,
                          String conversationType, String conversationId, String sender, String messageType, String content) {
            this(file, system, text, prefix, fileName, filePath, conversationType, conversationId, sender, messageType, content, "", "", "", "");
        }

        private ChatEntry(boolean file, boolean system, String text, String prefix, String fileName, String filePath,
                          String conversationType, String conversationId, String sender, String messageType, String content,
                          String replySender, String replyMessageType, String replyContent, String replyFileName) {
            this.file = file;
            this.system = system;
            this.text = text;
            this.prefix = prefix;
            this.fileName = fileName;
            this.filePath = filePath;
            this.conversationType = conversationType;
            this.conversationId = conversationId;
            this.sender = sender;
            this.messageType = messageType;
            this.content = content;
            this.replySender = replySender;
            this.replyMessageType = replyMessageType;
            this.replyContent = replyContent;
            this.replyFileName = replyFileName;
        }

        static ChatEntry text(String conversationType, String conversationId, String sender, String content, String displayText) {
            return text(conversationType, conversationId, sender, content, displayText, null, null, null, null);
        }

        static ChatEntry text(String conversationType, String conversationId, String sender, String content, String displayText,
                              String replySender, String replyMessageType, String replyContent, String replyFileName) {
            return new ChatEntry(false, false, displayText, "", "", "",
                    conversationType, conversationId, sender, MessageType.TEXT, content == null ? "" : content,
                    replySender == null ? "" : replySender,
                    replyMessageType == null ? "" : replyMessageType,
                    replyContent == null ? "" : replyContent,
                    replyFileName == null ? "" : replyFileName);
        }

        static ChatEntry file(String conversationType, String conversationId, String sender,
                              String prefix, String fileName, String filePath) {
            return new ChatEntry(true, false, "", prefix, fileName == null ? "" : fileName, filePath == null ? "" : filePath,
                    conversationType, conversationId, sender, MessageType.FILE, "");
        }

        static ChatEntry system(String text) {
            return new ChatEntry(false, true, text, "", "", "", "system", "system", "system", MessageType.TEXT, text == null ? "" : text);
        }
    }

    private static class StoredReply {
        final String sender;
        final String messageType;
        final String content;
        final String fileName;
        final String actualContent;

        StoredReply(String sender, String messageType, String content, String fileName) {
            this(sender, messageType, content, fileName, "");
        }

        StoredReply(String sender, String messageType, String content, String fileName, String actualContent) {
            this.sender = sender;
            this.messageType = messageType;
            this.content = content;
            this.fileName = fileName;
            this.actualContent = actualContent;
        }
    }

    private static class ForwardTarget {
        final String label;
        final String conversationType;
        final String conversationId;
        final String receiver;

        ForwardTarget(String label, String conversationType, String conversationId, String receiver) {
            this.label = label;
            this.conversationType = conversationType;
            this.conversationId = conversationId;
            this.receiver = receiver;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static class TypingTarget {
        final String conversationType;
        final String conversationId;
        final String receiver;

        TypingTarget(String conversationType, String conversationId, String receiver) {
            this.conversationType = conversationType;
            this.conversationId = conversationId;
            this.receiver = receiver;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof TypingTarget)) {
                return false;
            }
            TypingTarget other = (TypingTarget) obj;
            return same(conversationType, other.conversationType)
                    && same(conversationId, other.conversationId)
                    && same(receiver, other.receiver);
        }

        @Override
        public int hashCode() {
            int result = conversationType == null ? 0 : conversationType.hashCode();
            result = 31 * result + (conversationId == null ? 0 : conversationId.hashCode());
            result = 31 * result + (receiver == null ? 0 : receiver.hashCode());
            return result;
        }

        private boolean same(String left, String right) {
            return left == null ? right == null : left.equals(right);
        }
    }
}
