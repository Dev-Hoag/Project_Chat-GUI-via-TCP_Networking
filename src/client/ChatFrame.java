package client;

import common.ConversationType;
import common.MessageType;
import common.Protocol;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ChatFrame extends JFrame implements ClientSocketService.Listener {
    private final String username;
    private final ClientSocketService socketService;
    private final FileSender fileSender;

    private final JTextPane chatPane = new JTextPane();
    private final JTextField messageInput = new JTextField();
    private final JButton sendButton = new JButton("Send");
    private final JButton sendFileButton = new JButton("File");
    private final JButton createRoomButton = new JButton("Create Group");
    private final JCheckBox privateModeCheckBox = new JCheckBox("Private");
    private final JLabel statusLabel = new JLabel("Connecting...");
    private final DefaultListModel<String> onlineUsersModel = new DefaultListModel<String>();
    private final JList<String> onlineUserList = new JList<String>(onlineUsersModel);
    private final DefaultListModel<ConversationItem> conversationModel = new DefaultListModel<ConversationItem>();
    private final JList<ConversationItem> conversationList = new JList<ConversationItem>(conversationModel);

    private final Map<String, ConversationItem> conversations = new HashMap<String, ConversationItem>();
    private final Map<String, List<ChatEntry>> entriesByConversation = new HashMap<String, List<ChatEntry>>();
    private final Map<Integer, ChatEntry> renderedLines = new HashMap<Integer, ChatEntry>();
    private final Set<String> loadedHistory = new HashSet<String>();

    private boolean readyForHistory;
    private boolean connected;

    public ChatFrame(String username, ClientSocketService socketService) {
        this.username = username;
        this.socketService = socketService;
        this.fileSender = new FileSender(socketService);
        setTitle("TCPChatGUI - " + username);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(920, 620);
        setLocationRelativeTo(null);
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
    public void onDisconnected(String reason) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                connected = false;
                statusLabel.setText("Disconnected: " + reason);
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
                    connected = true;
                    loadedHistory.clear();
                }
            }
        });
    }

    private void initUi() {
        chatPane.setEditable(false);
        chatPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    downloadFileAtClick(e);
                }
            }
        });

        onlineUserList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        onlineUserList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openPrivateConversationFromSelectedUser();
                }
            }
        });

        conversationList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        conversationList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                requestSelectedConversationHistory();
                renderSelectedConversation();
            }
        });

        JPanel leftPanel = new JPanel(new BorderLayout(6, 6));
        leftPanel.setBorder(BorderFactory.createTitledBorder("Conversations"));
        leftPanel.add(new JScrollPane(conversationList), BorderLayout.CENTER);
        leftPanel.setPreferredSize(new Dimension(180, 400));

        JPanel rightPanel = new JPanel(new BorderLayout(6, 6));
        rightPanel.setBorder(BorderFactory.createTitledBorder("Online Users"));
        rightPanel.add(new JScrollPane(onlineUserList), BorderLayout.CENTER);
        rightPanel.add(createRoomButton, BorderLayout.SOUTH);
        rightPanel.setPreferredSize(new Dimension(180, 400));

        JSplitPane centerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, new JScrollPane(chatPane));
        centerSplit.setResizeWeight(0);
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, centerSplit, rightPanel);
        mainSplit.setResizeWeight(1);

        JPanel bottomPanel = new JPanel(new BorderLayout(8, 8));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        bottomPanel.add(messageInput, BorderLayout.CENTER);
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttonPanel.add(privateModeCheckBox);
        buttonPanel.add(sendFileButton);
        buttonPanel.add(sendButton);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);

        add(statusLabel, BorderLayout.NORTH);
        add(mainSplit, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        sendButton.addActionListener(e -> sendText());
        sendFileButton.addActionListener(e -> chooseAndSendFile());
        createRoomButton.addActionListener(e -> createRoom());
        messageInput.addActionListener(e -> sendText());

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                socketService.close();
            }
        });
    }

    private void handleServerMessage(Protocol.ParsedMessage message) {
        String command = message.getCommand();
        if (Protocol.LOGIN_SUCCESS.equals(command)) {
            statusLabel.setText("Logged in as " + message.field(0) + " | room: lobby");
            readyForHistory = true;
            connected = true;
            requestSelectedConversationHistory();
        } else if (Protocol.ERROR.equals(command)) {
            addSystemToCurrent("Error: " + message.field(0));
        } else if (Protocol.USER_LIST.equals(command)) {
            updateUsers(message.field(0));
        } else if (Protocol.JOIN.equals(command)) {
            addEntry(Protocol.LOBBY_ROOM_ID, ChatEntry.text("[System] " + message.field(0) + " joined the chat"));
        } else if (Protocol.LEAVE.equals(command)) {
            addEntry(Protocol.LOBBY_ROOM_ID, ChatEntry.text("[System] " + message.field(0) + " left the chat"));
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
            addEntry(message.field(0), ChatEntry.text(message.field(3) + " " + message.field(1) + ": " + message.field(2)));
        } else if (Protocol.PRIVATE_MSG_DELIVER.equals(command)) {
            String sender = message.field(0);
            String conversationId = Protocol.privateConversationId(username, sender);
            addPrivateConversation(sender, false);
            addEntry(conversationId, ChatEntry.text(message.field(2) + " " + sender + ": " + message.field(1)));
        } else if (Protocol.PRIVATE_MSG_SENT.equals(command)) {
            String receiver = message.field(0);
            String conversationId = Protocol.privateConversationId(username, receiver);
            addPrivateConversation(receiver, false);
            addEntry(conversationId, ChatEntry.text(message.field(2) + " Me: " + message.field(1)));
        } else if (Protocol.HISTORY.equals(command)) {
            appendHistory(message);
        } else if (Protocol.FILE_DELIVER.equals(command)) {
            handleFileDeliver(message);
        } else if (Protocol.ROOM_USERS.equals(command)) {
            addSystemToCurrent("Room " + message.field(0) + " users: " + message.field(1));
        }
    }

    private void sendText() {
        String text = messageInput.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        if (!connected) {
            addSystemToCurrent("Currently offline. Waiting for reconnect.");
            return;
        }
        try {
            ConversationItem conversation = getSelectedConversation();
            if (ConversationType.PRIVATE.equals(conversation.type)) {
                socketService.send(Protocol.build(Protocol.PRIVATE_MSG, conversation.receiver, text));
            } else if (privateModeCheckBox.isSelected()) {
                String receiver = getSelectedSingleUser();
                if (receiver == null) {
                    addSystemToCurrent("Select one online user for private chat");
                    return;
                }
                addPrivateConversation(receiver, true);
                socketService.send(Protocol.build(Protocol.PRIVATE_MSG, receiver, text));
            } else {
                socketService.send(Protocol.build(Protocol.ROOM_MSG, conversation.id, text));
            }
            messageInput.setText("");
        } catch (IOException e) {
            addSystemToCurrent("Cannot send message: " + e.getMessage());
        }
    }

    private void chooseAndSendFile() {
        if (!connected) {
            addSystemToCurrent("Currently offline. Waiting for reconnect.");
            return;
        }
        JFileChooser chooser = new JFileChooser();
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
            String user = onlineUsersModel.getElementAt(i);
            if (!username.equals(user)) {
                users.add(user);
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
        String conversationId = message.field(1);
        String sender = message.field(2);
        String messageType = message.field(4);
        String content = message.field(5);
        String fileName = message.field(6);
        String filePath = message.field(7);
        String time = message.field(8);

        if (MessageType.FILE.equals(messageType)) {
            addEntry(conversationId, ChatEntry.file("[History] " + time + " " + sender + ": ", fileName, filePath));
        } else {
            addEntry(conversationId, ChatEntry.text("[History] " + time + " " + sender + ": " + content));
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
        addEntry(conversationId, ChatEntry.file(time + " " + sender + " sent file: ", fileName, filePath));
    }

    private void updateUsers(String csvUsers) {
        onlineUsersModel.clear();
        if (csvUsers.trim().isEmpty()) {
            return;
        }
        for (String user : csvUsers.split(",")) {
            if (!user.trim().isEmpty()) {
                onlineUsersModel.addElement(user.trim());
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
        List<String> selected = onlineUserList.getSelectedValuesList();
        if (selected.size() != 1) {
            return null;
        }
        String receiver = selected.get(0);
        return receiver.equals(username) ? null : receiver;
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
        addEntry(conversation.id, ChatEntry.text("[System] " + text));
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
                } else {
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
        StyleConstants.setForeground(set, Color.DARK_GRAY);
        return set;
    }

    private SimpleAttributeSet fileStyle() {
        SimpleAttributeSet set = new SimpleAttributeSet();
        StyleConstants.setForeground(set, new Color(0, 92, 204));
        StyleConstants.setUnderline(set, true);
        StyleConstants.setBold(set, true);
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

    private static class ChatEntry {
        final boolean file;
        final String text;
        final String prefix;
        final String fileName;
        final String filePath;

        private ChatEntry(boolean file, String text, String prefix, String fileName, String filePath) {
            this.file = file;
            this.text = text;
            this.prefix = prefix;
            this.fileName = fileName;
            this.filePath = filePath;
        }

        static ChatEntry text(String text) {
            return new ChatEntry(false, text, "", "", "");
        }

        static ChatEntry file(String prefix, String fileName, String filePath) {
            return new ChatEntry(true, "", prefix, fileName, filePath);
        }
    }
}
