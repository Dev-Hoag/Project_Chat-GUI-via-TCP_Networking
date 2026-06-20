package client.gui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import javax.swing.plaf.FontUIResource;
import java.awt.Color;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public final class AppTheme {
    public static final Color BACKGROUND = new Color(241, 245, 249);
    public static final Color SURFACE = Color.WHITE;
    public static final Color SIDEBAR = new Color(248, 250, 252);
    public static final Color HEADER = new Color(30, 58, 95);
    public static final Color PRIMARY = new Color(37, 99, 235);
    public static final Color PRIMARY_HOVER = new Color(29, 78, 216);
    public static final Color SECONDARY = new Color(226, 232, 240);
    public static final Color SECONDARY_HOVER = new Color(203, 213, 225);
    public static final Color TEXT = new Color(15, 23, 42);
    public static final Color TEXT_MUTED = new Color(100, 116, 139);
    public static final Color BORDER = new Color(226, 232, 240);
    public static final Color CHAT_BG = new Color(252, 252, 253);
    public static final Color ACCENT = new Color(16, 185, 129);
    public static final Color WARNING = new Color(180, 83, 9);
    public static final Color FILE_LINK = new Color(37, 99, 235);
    public static final Color REPLY = new Color(120, 113, 108);
    public static final Color SYSTEM = new Color(148, 163, 184);
    public static final Color SELECTION = new Color(219, 234, 254);

    private static Font fontBase;
    private static Font fontTitle;
    private static Font fontSmall;
    private static Font fontButton;

    private AppTheme() {
    }

    public static void install() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        Font system = UIManager.getFont("Label.font");
        if (system == null) {
            system = new Font("Segoe UI", Font.PLAIN, 13);
        }
        fontBase = system.deriveFont(13f);
        fontTitle = system.deriveFont(Font.BOLD, 20f);
        fontSmall = system.deriveFont(11f);
        fontButton = system.deriveFont(Font.BOLD, 12f);

        UIManager.put("Label.font", new FontUIResource(fontBase));
        UIManager.put("Button.font", new FontUIResource(fontButton));
        UIManager.put("TextField.font", new FontUIResource(fontBase));
        UIManager.put("TextPane.font", new FontUIResource(fontBase));
        UIManager.put("List.font", new FontUIResource(fontBase));
        UIManager.put("CheckBox.font", new FontUIResource(fontBase));
        UIManager.put("RadioButton.font", new FontUIResource(fontBase));
        UIManager.put("OptionPane.font", new FontUIResource(fontBase));
        UIManager.put("ScrollPane.border", BorderFactory.createEmptyBorder());
    }

    public static Font baseFont() {
        return fontBase != null ? fontBase : new Font("Dialog", Font.PLAIN, 13);
    }

    public static Font titleFont() {
        return fontTitle != null ? fontTitle : baseFont().deriveFont(Font.BOLD, 20f);
    }

    public static Font smallFont() {
        return fontSmall != null ? fontSmall : baseFont().deriveFont(11f);
    }

    public static void styleFrame(Container root) {
        root.setBackground(BACKGROUND);
    }

    public static void styleMutedLabel(JLabel label) {
        label.setFont(smallFont());
        label.setForeground(TEXT_MUTED);
    }

    public static void styleFieldLabel(JLabel label) {
        label.setFont(baseFont().deriveFont(Font.BOLD));
        label.setForeground(TEXT);
    }

    public static void styleTextField(JTextField field) {
        field.setUI(new javax.swing.plaf.basic.BasicTextFieldUI());
        field.setFont(baseFont());
        field.setOpaque(true);
        field.setBackground(SURFACE);
        field.setForeground(TEXT);
        field.setBorder(new CompoundBorder(
                new LineBorder(BORDER, 1, true),
                new EmptyBorder(8, 10, 8, 10)));
        field.setCaretColor(PRIMARY);
        field.setSelectedTextColor(SURFACE);
        field.setSelectionColor(PRIMARY);
    }

    public static void stylePrimaryButton(JButton button) {
        styleButton(button, PRIMARY, PRIMARY_HOVER, Color.WHITE, true);
    }

    public static void styleSecondaryButton(JButton button) {
        styleButton(button, SECONDARY, SECONDARY_HOVER, TEXT, false);
    }

    public static void styleGhostButton(JButton button) {
        styleButton(button, SURFACE, SECONDARY, TEXT_MUTED, false);
        button.setBorder(new CompoundBorder(
                new LineBorder(BORDER, 1, true),
                new EmptyBorder(6, 12, 6, 12)));
    }

    private static void styleButton(JButton button, Color bg, Color hover, Color fg, boolean bold) {
        button.setFont(bold ? fontButton : baseFont());
        button.setBackground(bg);
        button.setForeground(fg);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(new EmptyBorder(8, 14, 8, 14));
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(hover);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(bg);
            }
        });
    }

    public static JPanel createCard() {
        JPanel card = new JPanel();
        card.setBackground(SURFACE);
        card.setBorder(new CompoundBorder(
                new LineBorder(BORDER, 1, true),
                new EmptyBorder(20, 24, 20, 24)));
        return card;
    }

    public static JPanel createHeader(String title, String subtitle) {
        JPanel header = new JPanel(new java.awt.BorderLayout(0, 4));
        header.setBackground(HEADER);
        header.setBorder(new EmptyBorder(14, 18, 14, 18));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleFont());
        titleLabel.setForeground(Color.WHITE);
        header.add(titleLabel, java.awt.BorderLayout.NORTH);

        if (subtitle != null && !subtitle.isEmpty()) {
            JLabel subtitleLabel = new JLabel(subtitle);
            subtitleLabel.setFont(smallFont());
            subtitleLabel.setForeground(new Color(191, 219, 254));
            header.add(subtitleLabel, java.awt.BorderLayout.SOUTH);
        }
        return header;
    }

    public static JPanel createSidebar(String title) {
        JPanel panel = new JPanel(new java.awt.BorderLayout(0, 8));
        panel.setBackground(SIDEBAR);
        panel.setBorder(new CompoundBorder(
                new MatteBorder(0, 0, 0, 1, BORDER),
                new EmptyBorder(10, 10, 10, 10)));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(baseFont().deriveFont(Font.BOLD));
        titleLabel.setForeground(TEXT);
        titleLabel.setBorder(new EmptyBorder(0, 4, 4, 0));
        panel.add(titleLabel, java.awt.BorderLayout.NORTH);
        return panel;
    }

    public static JScrollPane wrapList(JList<?> list) {
        list.setBackground(SURFACE);
        list.setFixedCellHeight(42);
        list.setBorder(BorderFactory.createEmptyBorder());
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(new LineBorder(BORDER, 1, true));
        scroll.getViewport().setBackground(SURFACE);
        return scroll;
    }

    public static void styleChatPane(JTextPane pane) {
        pane.setBackground(CHAT_BG);
        pane.setForeground(TEXT);
        pane.setFont(baseFont());
        pane.setBorder(new EmptyBorder(14, 16, 14, 16));
        pane.setMargin(new java.awt.Insets(0, 0, 0, 0));
    }

    public static void styleInputField(JTextField field) {
        styleTextField(field);
        field.setPreferredSize(new Dimension(200, 38));
    }

    public static void styleStatusBar(JLabel label) {
        label.setFont(smallFont());
        label.setForeground(new Color(191, 219, 254));
    }

    public static void styleTypingLabel(JLabel label) {
        label.setFont(smallFont().deriveFont(Font.ITALIC));
        label.setForeground(ACCENT);
    }

    public static void styleProfileLabel(JLabel label) {
        label.setFont(baseFont());
        label.setForeground(Color.WHITE);
    }

    public static Border sectionBorder() {
        return new CompoundBorder(
                new MatteBorder(1, 0, 0, 0, BORDER),
                new EmptyBorder(10, 12, 10, 12));
    }

    public static void styleAvatarPreview(JLabel label, int size) {
        label.setPreferredSize(new Dimension(size, size));
        label.setMinimumSize(new Dimension(size, size));
        label.setHorizontalAlignment(JLabel.CENTER);
        label.setVerticalAlignment(JLabel.CENTER);
        label.setBackground(SECONDARY);
        label.setOpaque(true);
        label.setBorder(new LineBorder(BORDER, 1, true));
        label.setForeground(TEXT_MUTED);
        label.setFont(smallFont());
    }
}
