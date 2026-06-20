package client.gui;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.UIManager;
import javax.swing.filechooser.FileView;

import java.io.File;

/**
 * Tránh lỗi NPE trên Windows khi JFileChooser gọi FileSystemView.getSystemIcon()
 * (Win32ShellFolder2$MultiResolutionIconImage).
 */
public final class SafeFileChooser {
    private static final FileView SAFE_FILE_VIEW = new FileView() {
        @Override
        public Icon getIcon(File file) {
            if (file == null) {
                return null;
            }
            String key = file.isDirectory() ? "FileView.directoryIcon" : "FileView.fileIcon";
            Icon icon = UIManager.getIcon(key);
            return icon != null ? icon : new ImageIcon();
        }
    };

    private SafeFileChooser() {
    }

    public static JFileChooser create() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileView(SAFE_FILE_VIEW);
        return chooser;
    }
}
