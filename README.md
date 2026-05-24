# TCPChatGUI

TCPChatGUI là ứng dụng Java Desktop Chat dùng Java Swing và TCP Socket. Project có server-client, nhiều client kết nối cùng lúc, chat trong room chung, tạo group chat, chat riêng 1-1, danh sách user online, thông báo join/leave, gửi file, tải file, lưu lịch sử tin nhắn vào Supabase PostgreSQL.

Ứng dụng không phải Web App. Client chạy bằng cửa sổ Java Swing trên Windows.

## Tính Năng Chính

- Login bằng username, không cần mật khẩu.
- Server TCP dùng `ServerSocket`.
- Client TCP dùng `Socket`.
- Mỗi client trên server chạy bằng một thread riêng.
- Nhiều client chat cùng lúc.
- Có room chung mặc định `Lobby`, ai login cũng tự vào.
- Tạo group chat nhiều người.
- Chat riêng 1-1 bằng cách double click user online.
- Mỗi conversation có nội dung riêng, không lẫn Lobby/private/group.
- User online list cập nhật realtime.
- Join/leave notification.
- Gửi file dưới 10MB.
- File thật lưu trên server trong `server_files/`.
- Database lưu metadata file: `file_name`, `file_path`.
- Client double click tên file màu xanh để tải về thư mục `downloads/`.
- Lưu lịch sử tin nhắn vào Supabase PostgreSQL.
- Login lại vẫn load lại group/private conversations đã lưu DB.
- Client có auto reconnect khi mất kết nối tạm thời.
- Hỗ trợ UTF-8, gửi được tiếng Việt.

## Yêu Cầu Cài Đặt

Máy chạy cần có:

- Windows
- Java JDK 8 trở lên
- PostgreSQL JDBC driver tại:

```text
lib/postgresql-jdbc.jar
```

Project đã có `pom.xml` cho Maven/IntelliJ, nhưng cách chạy demo chính là dùng file `.bat`, không bắt buộc cài Maven.

## Cách Chạy Nhanh Không Cần Maven

Mở thư mục project:

```text
D:\java\ltm\untitled
```

Chạy theo thứ tự:

1. Double click:

```text
compile.bat
```

2. Double click:

```text
run-server.bat
```

3. Mở thêm một hoặc nhiều client bằng cách double click:

```text
run-client.bat
```

Muốn test 3 client thì double click `run-client.bat` 3 lần, mỗi cửa sổ login username khác nhau.

## Cách Chạy Bằng PowerShell

Compile:

```powershell
cd D:\java\ltm\untitled
javac -encoding UTF-8 -cp ".;lib\postgresql-jdbc.jar" -d out src\common\*.java src\server\*.java src\client\*.java
```

Chạy server:

```powershell
java -cp "out;lib\postgresql-jdbc.jar" server.ChatServer 5000
```

Chạy client:

```powershell
java -cp "out;lib\postgresql-jdbc.jar" client.ChatClient 127.0.0.1 5000
```

## Cấu Hình Supabase PostgreSQL

Project đọc cấu hình DB từ file `.env` ở thư mục gốc.

File `.env`:

```env
SUPABASE_DB_URL=jdbc:postgresql://HOST:PORT/postgres?sslmode=require
SUPABASE_DB_USER=postgres.xxxxx
SUPABASE_DB_PASSWORD=your-password
```

Ví dụ:

```env
SUPABASE_DB_URL=jdbc:postgresql://aws-1-ap-southeast-2.pooler.supabase.com:5432/postgres?sslmode=require
SUPABASE_DB_USER=postgres.prqypbfauhcxwnidcdxr
SUPABASE_DB_PASSWORD=your-database-password
```

Không commit hoặc chia sẻ password thật. File `.env` đã được đưa vào `.gitignore`.

Nếu không cấu hình DB, server vẫn có thể chạy để demo TCP chat, nhưng sẽ không lưu lịch sử.

## Database Tables

Server tự tạo bảng nếu chưa có.

### `messages`

Lưu tin nhắn text và metadata file.

```sql
CREATE TABLE IF NOT EXISTS messages (
    id BIGSERIAL PRIMARY KEY,
    sender TEXT NOT NULL,
    conversation_type TEXT NOT NULL,
    conversation_id TEXT NOT NULL,
    receiver TEXT,
    message_type TEXT NOT NULL,
    content TEXT,
    file_name TEXT,
    file_path TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### `rooms`

Lưu thông tin group chat.

```sql
CREATE TABLE IF NOT EXISTS rooms (
    room_id TEXT PRIMARY KEY,
    room_name TEXT NOT NULL,
    created_by TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### `room_members`

Lưu thành viên của từng group.

```sql
CREATE TABLE IF NOT EXISTS room_members (
    room_id TEXT NOT NULL,
    username TEXT NOT NULL,
    PRIMARY KEY (room_id, username)
);
```

### `private_conversations`

Lưu conversation chat riêng 1-1.

```sql
CREATE TABLE IF NOT EXISTS private_conversations (
    conversation_id TEXT PRIMARY KEY,
    user_a TEXT NOT NULL,
    user_b TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## Cấu Trúc Thư Mục

```text
TCPChatGUI/
├── README.md
├── pom.xml
├── compile.bat
├── run-server.bat
├── run-client.bat
├── clean.bat
├── .env
├── .env.example
├── lib/
│   └── postgresql-jdbc.jar
├── server_files/
├── downloads/
├── logs/
├── out/
└── src/
    ├── common/
    ├── server/
    └── client/
```

## Ý Nghĩa Các Thư Mục

### `src/common`

Chứa code dùng chung cho cả server và client.

### `src/server`

Chứa toàn bộ TCP server, xử lý login, routing message, quản lý user online, quản lý room, lưu DB, lưu file.

### `src/client`

Chứa client desktop Java Swing, socket service, GUI login/chat, gửi file và tải file.

### `lib`

Chứa PostgreSQL JDBC driver.

```text
lib/postgresql-jdbc.jar
```

File này cần có để server kết nối Supabase PostgreSQL khi chạy bằng `javac/java`.

### `server_files`

Thư mục server lưu file thật do client gửi lên.

Ví dụ:

```text
server_files/20260524225209_user_document.docx
```

### `downloads`

Thư mục client lưu file tải về sau khi double click tên file trong chat.

Ví dụ:

```text
downloads/document.docx
```

### `logs`

Chứa log khi chạy server bằng process nền trong quá trình demo/debug.

### `out`

Chứa file `.class` sau khi compile bằng `javac`.

## Chức Năng Các File Common

### `src/common/Protocol.java`

Định nghĩa protocol text giữa server và client.

Chức năng:

- Khai báo command như `LOGIN`, `ROOM_MSG`, `PRIVATE_MSG`, `FILE_META`, `HISTORY`.
- Build message dạng:

```text
COMMAND|field1|field2
```

- Encode/decode từng field bằng UTF-8 để không lỗi tiếng Việt hoặc ký tự `|`.
- Đọc/ghi line qua socket.
- Tạo `privateConversationId` thống nhất cho chat 1-1.

### `src/common/ConversationType.java`

Khai báo loại conversation:

```text
LOBBY
ROOM
PRIVATE
```

### `src/common/MessageType.java`

Khai báo loại message:

```text
TEXT
FILE
```

## Chức Năng Các File Server

### `src/server/ChatServer.java`

Main class chạy server.

Chức năng:

- Mở `ServerSocket`.
- Lắng nghe client kết nối.
- Tạo `ClientHandler` cho mỗi client.
- Mỗi client chạy trên một thread riêng.

Main:

```text
server.ChatServer
```

### `src/server/ClientHandler.java`

Đại diện cho một client đang kết nối tới server.

Chức năng:

- Đọc command từ socket.
- Xử lý login ban đầu.
- Nhận message text.
- Nhận file upload.
- Nhận yêu cầu download file.
- Bắt lỗi khi client tắt đột ngột.
- Đóng socket an toàn.

### `src/server/ClientManager.java`

Quản lý trạng thái online và room trong RAM.

Chức năng:

- Lưu online users bằng `ConcurrentHashMap<String, ClientHandler>`.
- Add/remove client.
- Lấy danh sách online users.
- Quản lý room `Lobby` và group room.
- Kiểm tra user có thuộc room không.

### `src/server/MessageRouter.java`

Điều phối toàn bộ message server.

Chức năng:

- Broadcast message trong room.
- Gửi private message đúng người.
- Tạo group chat.
- Gửi `USER_LIST`.
- Gửi `ROOM_LIST`.
- Gửi `PRIVATE_LIST`.
- Gửi join/leave notification.
- Gửi lịch sử conversation.
- Gọi DB để lưu message, room, private conversation.
- Gửi file download về client.

### `src/server/DatabaseManager.java`

Quản lý kết nối Supabase PostgreSQL.

Chức năng:

- Đọc `.env` hoặc biến môi trường.
- Load PostgreSQL JDBC driver.
- Tự tạo bảng nếu chưa có.
- Lưu text message.
- Lưu file metadata.
- Lưu group room.
- Lưu room members.
- Lưu private conversations.
- Load history 20 tin gần nhất.
- Load lại group/private khi user login lại.

### `src/server/FileService.java`

Xử lý file trên server.

Chức năng:

- Nhận bytes file từ client.
- Giới hạn file dưới 10MB.
- Lưu file vào `server_files/`.
- Đặt tên file có timestamp để tránh trùng.
- Kiểm tra path an toàn khi client yêu cầu download.
- Gửi bytes file về client.

### `src/server/ChatRoom.java`

Model room chat.

Chức năng:

- Lưu `roomId`.
- Lưu `roomName`.
- Lưu danh sách members.
- Add/remove/check member.

## Chức Năng Các File Client

### `src/client/ChatClient.java`

Main class chạy client.

Chức năng:

- Đọc host/port từ args.
- Mặc định kết nối `127.0.0.1:5000`.
- Mở `LoginFrame`.

Main:

```text
client.ChatClient
```

### `src/client/LoginFrame.java`

GUI login.

Chức năng:

- Nhập username.
- Kết nối server.
- Gửi `LOGIN|username`.
- Nếu kết nối thành công thì mở `ChatFrame`.

### `src/client/ChatFrame.java`

GUI chat chính.

Chức năng:

- Hiển thị danh sách conversations bên trái.
- Hiển thị nội dung chat ở giữa.
- Hiển thị online users bên phải.
- Gửi message.
- Gửi file.
- Double click user để mở private chat.
- Tạo group bằng checkbox.
- Hiển thị file dưới dạng link xanh.
- Double click tên file để tải về `downloads/`.
- Tách nội dung từng conversation, không lẫn Lobby/private/group.
- Hiển thị trạng thái disconnected/reconnecting.

### `src/client/ClientSocketService.java`

Quản lý socket phía client.

Chức năng:

- Kết nối server.
- Gửi command text.
- Gửi file bytes.
- Nhận message từ server ở background thread.
- Nhận file download.
- Auto reconnect khi mất kết nối.
- Không block Swing UI.

### `src/client/FileSender.java`

Wrapper gửi file.

Chức năng:

- Kiểm tra file dưới 10MB.
- Gọi `ClientSocketService.sendFile`.

## Protocol Chính

Một số command quan trọng:

```text
LOGIN
LOGIN_SUCCESS
ERROR
USER_LIST
JOIN
LEAVE
ROOM_LIST
ROOM_USERS
CREATE_ROOM
CREATE_ROOM_SUCCESS
ROOM_INVITE
ROOM_MSG
ROOM_MSG_DELIVER
PRIVATE_LIST
PRIVATE_MSG
PRIVATE_MSG_DELIVER
PRIVATE_MSG_SENT
HISTORY_REQUEST
HISTORY
FILE_META
FILE_DELIVER
FILE_DOWNLOAD
FILE_DOWNLOAD_META
```

## Hướng Dẫn Demo

1. Chạy `compile.bat`.
2. Chạy `run-server.bat`.
3. Chạy `run-client.bat` 3 lần.
4. Login 3 username khác nhau.
5. Test chat trong `Lobby`.
6. Double click một user để mở private chat.
7. Gửi private message và kiểm tra user thứ ba không thấy.
8. Bấm `Create Group`, tick nhiều user, tạo group.
9. Chọn group và gửi tin nhắn.
10. Gửi file dưới 10MB.
11. Client nhận file, double click tên file màu xanh để tải về.
12. Tắt một client, mở lại bằng username cũ.
13. Kiểm tra group/private conversations được load lại.
14. Tắt server rồi bật lại để kiểm tra client auto reconnect.

## Lỗi Thường Gặp

### `Address already in use`

Port `5000` đang có server cũ chạy.

PowerShell:

```powershell
Stop-Process -Id (Get-NetTCPConnection -LocalPort 5000 -State Listen).OwningProcess -Force
```

### `No suitable driver found`

Thiếu PostgreSQL JDBC driver trong classpath.

Kiểm tra file:

```text
lib/postgresql-jdbc.jar
```

Chạy đúng lệnh:

```powershell
java -cp "out;lib\postgresql-jdbc.jar" server.ChatServer 5000
```

### `javac is not recognized`

Máy chưa cài JDK hoặc chưa thêm JDK vào PATH.

Cần cài Java JDK, không chỉ JRE.

### Client không thấy group/private cũ

Kiểm tra:

- `.env` đúng chưa
- server có kết nối được Supabase không
- bảng `rooms`, `room_members`, `private_conversations` có được tạo không

## Maven

Project có `pom.xml` để dùng với IntelliJ/Maven.

Nếu có Maven:

```powershell
mvn compile
mvn exec:java -Dexec.mainClass="server.ChatServer" -Dexec.args="5000"
mvn exec:java -Dexec.mainClass="client.ChatClient" -Dexec.args="127.0.0.1 5000"
```

Nếu dùng IntelliJ:

1. Right click `pom.xml`.
2. Chọn `Add as Maven Project`.
3. Đợi IntelliJ tải dependency.
4. Chạy `server.ChatServer` hoặc `client.ChatClient`.

## Ghi Chú Bảo Mật

- Không hard-code password Supabase trong source code.
- Không chia sẻ `.env` thật.
- File `.env` đã được ignore trong `.gitignore`.
- File gửi lên server chỉ cho download nếu nằm trong thư mục `server_files/`.
