package server.service.file;

import common.Protocol;
import server.service.file.IFileService;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class FileService implements IFileService {
    // Quản lý thư mục lưu file upload trên server và hỗ trợ truy xuất file để download.
    private final File serverFilesDir;

    // Khởi tạo thư mục server_files để lưu các file upload từ client.
    public FileService() {
        this.serverFilesDir = new File("server_files");
        if (!serverFilesDir.exists() && !serverFilesDir.mkdirs()) {
            System.out.println("[WARN] Cannot create server_files directory.");
        }
    }

    // Nhận file từ client qua socket. Đọc chính xác fileSize byte sau metadata file.
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

    // Kiểm tra và trả về file để server gửi lại cho client khi client request download.
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

    // Ghi file ra output stream của socket để gửi file về client.
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

    public static class StoredFile extends IFileService.StoredFile {
        public StoredFile(String originalFileName, String savedPath) {
            super(originalFileName, savedPath);
        }
    }
}
