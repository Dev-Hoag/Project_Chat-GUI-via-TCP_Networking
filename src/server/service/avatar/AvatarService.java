package server.service.avatar;

import common.Protocol;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class AvatarService {
    private static final String[] ALLOWED_EXTENSIONS = {".png", ".jpg", ".jpeg", ".gif", ".webp"};
    private final File avatarDir;

    public AvatarService() {
        this.avatarDir = new File("server_files/avatars");
        if (!avatarDir.exists() && !avatarDir.mkdirs()) {
            System.out.println("[WARN] Cannot create avatar directory.");
        }
    }

    public StoredAvatar saveAvatar(InputStream inputStream, String username, String originalFileName, long fileSize) throws IOException {
        if (fileSize < 0 || fileSize > Protocol.MAX_AVATAR_SIZE_BYTES) {
            throw new IOException("Avatar must be smaller than 2MB");
        }
        String extension = getExtension(originalFileName);
        if (!isAllowedExtension(extension)) {
            throw new IOException("Avatar must be png, jpg, jpeg, gif, or webp");
        }
        String safeUsername = sanitize(username);
        String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        File savedFile = new File(avatarDir, safeUsername + "_" + timestamp + extension);

        long remaining = fileSize;
        byte[] buffer = new byte[8192];
        try (FileOutputStream fileOutputStream = new FileOutputStream(savedFile)) {
            while (remaining > 0) {
                int read = inputStream.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read == -1) {
                    throw new IOException("Connection closed while receiving avatar");
                }
                fileOutputStream.write(buffer, 0, read);
                remaining -= read;
            }
        }
        return new StoredAvatar(savedFile.getPath(), fileSize);
    }

    public File getAvatarFile(String storedPath) throws IOException {
        if (storedPath == null || storedPath.trim().isEmpty()) {
            throw new IOException("Avatar not found");
        }
        File requested = new File(storedPath);
        File canonicalBase = avatarDir.getCanonicalFile();
        File canonicalFile = requested.getCanonicalFile();
        if (!canonicalFile.getPath().startsWith(canonicalBase.getPath() + File.separator)) {
            throw new IOException("Invalid avatar path");
        }
        if (!canonicalFile.exists() || !canonicalFile.isFile()) {
            throw new IOException("Avatar not found");
        }
        return canonicalFile;
    }

    public void writeAvatarToOutput(File file, OutputStream outputStream) throws IOException {
        byte[] buffer = new byte[8192];
        try (java.io.FileInputStream fileInputStream = new java.io.FileInputStream(file)) {
            int read;
            while ((read = fileInputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            outputStream.flush();
        }
    }

    private boolean isAllowedExtension(String extension) {
        for (String allowed : ALLOWED_EXTENSIONS) {
            if (allowed.equalsIgnoreCase(extension)) {
                return true;
            }
        }
        return false;
    }

    private String getExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int index = fileName.lastIndexOf('.');
        if (index < 0) {
            return "";
        }
        return fileName.substring(index).toLowerCase();
    }

    private String sanitize(String value) {
        String cleaned = value == null ? "avatar" : value.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return cleaned.isEmpty() ? "avatar" : cleaned;
    }

    public static class StoredAvatar {
        public final String path;
        public final long size;

        public StoredAvatar(String path, long size) {
            this.path = path;
            this.size = size;
        }
    }
}
