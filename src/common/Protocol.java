package common;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

public final class Protocol {
    public static final String LOGIN = "LOGIN";
    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_ERROR = "LOGIN_ERROR";
    public static final String REGISTER = "REGISTER";
    public static final String REGISTER_SUCCESS = "REGISTER_SUCCESS";
    public static final String REGISTER_ERROR = "REGISTER_ERROR";
    public static final String ERROR = "ERROR";
    public static final String USER_LIST = "USER_LIST";
    public static final String JOIN = "JOIN";
    public static final String LEAVE = "LEAVE";

    public static final String ROOM_LIST = "ROOM_LIST";
    public static final String ROOM_USERS = "ROOM_USERS";
    public static final String CREATE_ROOM = "CREATE_ROOM";
    public static final String CREATE_ROOM_SUCCESS = "CREATE_ROOM_SUCCESS";
    public static final String ROOM_INVITE = "ROOM_INVITE";
    public static final String JOIN_ROOM = "JOIN_ROOM";
    public static final String LEAVE_ROOM = "LEAVE_ROOM";
    public static final String ROOM_MSG = "ROOM_MSG";
    public static final String ROOM_MSG_DELIVER = "ROOM_MSG_DELIVER";
    public static final String ROOM_MSG_SENT = "ROOM_MSG_SENT";

    public static final String PRIVATE_MSG = "PRIVATE_MSG";
    public static final String PRIVATE_MSG_DELIVER = "PRIVATE_MSG_DELIVER";
    public static final String PRIVATE_MSG_SENT = "PRIVATE_MSG_SENT";
    public static final String FORWARD_MSG = "FORWARD_MSG";
    public static final String PRIVATE_LIST = "PRIVATE_LIST";
    public static final String TYPING_START = "TYPING_START";
    public static final String TYPING_STOP = "TYPING_STOP";
    public static final String TYPING_STATUS = "TYPING_STATUS";

    public static final String HISTORY_REQUEST = "HISTORY_REQUEST";
    public static final String HISTORY = "HISTORY";

    public static final String FILE_META = "FILE_META";
    public static final String FILE_DELIVER = "FILE_DELIVER";
    public static final String FILE_DOWNLOAD = "FILE_DOWNLOAD";
    public static final String FILE_DOWNLOAD_META = "FILE_DOWNLOAD_META";
    public static final String AVATAR_SET = "AVATAR_SET";
    public static final String AVATAR_SET_SUCCESS = "AVATAR_SET_SUCCESS";
    public static final String AVATAR_SET_ERROR = "AVATAR_SET_ERROR";
    public static final String AVATAR_REQUEST = "AVATAR_REQUEST";
    public static final String AVATAR_DELIVER = "AVATAR_DELIVER";

    public static final String LOBBY_ROOM_ID = "lobby";
    public static final String LOBBY_ROOM_NAME = "Lobby";
    public static final int MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024;
    public static final int MAX_AVATAR_SIZE_BYTES = 2 * 1024 * 1024;

    private Protocol() {
    }

    public static String build(String command, String... fields) {
        StringBuilder builder = new StringBuilder(command);
        for (String field : fields) {
            builder.append('|').append(encode(field));
        }
        return builder.toString();
    }

    public static ParsedMessage parse(String line) {
        String[] rawParts = line.split("\\|", -1);
        List<String> fields = new ArrayList<String>();
        for (int i = 1; i < rawParts.length; i++) {
            fields.add(decode(rawParts[i]));
        }
        return new ParsedMessage(rawParts[0], fields);
    }

    public static String encode(String value) {
        String safeValue = value == null ? "" : value;
        try {
            return URLEncoder.encode(safeValue, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 is not supported", e);
        }
    }

    public static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 is not supported", e);
        }
    }

    public static void writeLine(OutputStream outputStream, String line) throws IOException {
        outputStream.write(line.getBytes(StandardCharsets.UTF_8));
        outputStream.write('\n');
        outputStream.flush();
    }

    public static String readLine(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(256);
        while (true) {
            int value = inputStream.read();
            if (value == -1) {
                if (buffer.size() == 0) {
                    throw new EOFException("Connection closed");
                }
                break;
            }
            if (value == '\n') {
                break;
            }
            if (value != '\r') {
                buffer.write(value);
            }
        }
        return buffer.toString(StandardCharsets.UTF_8.name());
    }

    public static String privateConversationId(String userA, String userB) {
        if (userA.compareToIgnoreCase(userB) <= 0) {
            return "private_" + userA + "_" + userB;
        }
        return "private_" + userB + "_" + userA;
    }

    public static final class ParsedMessage {
        private final String command;
        private final List<String> fields;

        public ParsedMessage(String command, List<String> fields) {
            this.command = command;
            this.fields = fields;
        }

        public String getCommand() {
            return command;
        }

        public List<String> getFields() {
            return fields;
        }

        public String field(int index) {
            return index >= 0 && index < fields.size() ? fields.get(index) : "";
        }
    }
}
