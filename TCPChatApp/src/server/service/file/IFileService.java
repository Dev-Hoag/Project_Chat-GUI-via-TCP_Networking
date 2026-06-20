package server.service.file;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

// Interface cho file service - dễ test và mock
public interface IFileService {
    // Receive file from client
    StoredFile receiveFile(InputStream inputStream, String username, String originalFileName, long fileSize) throws IOException;
    
    // Download file to client
    File getDownloadFile(String filePath) throws IOException;
    void writeFileToOutput(File file, OutputStream outputStream) throws IOException;
    
    // DTO
    public static class StoredFile {
        public final String originalFileName;
        public final String savedPath;
        
        public StoredFile(String originalFileName, String savedPath) {
            this.originalFileName = originalFileName;
            this.savedPath = savedPath;
        }
    }
}
