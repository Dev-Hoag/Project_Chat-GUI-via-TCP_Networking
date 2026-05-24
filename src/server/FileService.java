package server;

import common.Protocol;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class FileService {
    private final File serverFilesDir;

    public FileService() {
        this.serverFilesDir = new File("server_files");
        if (!serverFilesDir.exists() && !serverFilesDir.mkdirs()) {
            System.out.println("[WARN] Cannot create server_files directory.");
        }
    }

    public StoredFile receiveFile(InputStream inputStream, String username, String originalFileName, long fileSize) throws IOException {
        if (fileSize < 0 || fileSize > Protocol.MAX_FILE_SIZE_BYTES) {
            throw new IOException("File size must be from 0 to 10MB");
        }
        String safeOriginalName = sanitizeFileName(originalFileName);
        String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        File savedFile = new File(serverFilesDir, timestamp + "_" + sanitizeFileName(username) + "_" + safeOriginalName);

        long remaining = fileSize;
        byte[] buffer = new byte[8192];
        try (FileOutputStream fileOutputStream = new FileOutputStream(savedFile)) {
            while (remaining > 0) {
                int read = inputStream.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read == -1) {
                    throw new IOException("Connection closed while receiving file");
                }
                fileOutputStream.write(buffer, 0, read);
                remaining -= read;
            }
        }
        return new StoredFile(originalFileName, savedFile.getPath());
    }

    public File getDownloadFile(String filePath) throws IOException {
        File requested = new File(filePath);
        File canonicalBase = serverFilesDir.getCanonicalFile();
        File canonicalFile = requested.getCanonicalFile();
        if (!canonicalFile.getPath().startsWith(canonicalBase.getPath() + File.separator)) {
            throw new IOException("Invalid file path");
        }
        if (!canonicalFile.exists() || !canonicalFile.isFile()) {
            throw new IOException("File not found");
        }
        return canonicalFile;
    }

    public void writeFileToOutput(File file, java.io.OutputStream outputStream) throws IOException {
        byte[] buffer = new byte[8192];
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            int read;
            while ((read = fileInputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            outputStream.flush();
        }
    }

    private String sanitizeFileName(String value) {
        String cleaned = value == null ? "file" : value.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return cleaned.isEmpty() ? "file" : cleaned;
    }

    public static class StoredFile {
        public final String originalFileName;
        public final String savedPath;

        public StoredFile(String originalFileName, String savedPath) {
            this.originalFileName = originalFileName;
            this.savedPath = savedPath;
        }
    }
}
