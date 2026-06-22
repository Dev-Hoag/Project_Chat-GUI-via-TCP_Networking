package com.chatapp.common;

public class Message {
    private final MessageType type;
    private final String sender;
    private final String receiver;
    private final String text;

    public Message(MessageType type, String sender, String receiver, String text) {
        this.type = type;
        this.sender = sender;
        this.receiver = receiver;
        this.text = text;
    }

    public Message(String type, String sender, String receiver, String text) {
        this(MessageType.fromString(type), sender, receiver, text);
    }

    public MessageType getType() {
        return type;
    }

    public String getSender() {
        return sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public String getText() {
        return text;
    }

    // Doi message thanh chuoi de gui qua socket.
    // Dang chuoi: TYPE|sender|receiver|text
    public String toProtocolString() {
        return type + "|" + sender + "|" + receiver + "|" + text;
    }

    // Doc chuoi nhan duoc tu socket va doi lai thanh Message.
    public static Message fromProtocolString(String rawMessage) {
        String[] parts = rawMessage.split("\\|", 4);

        String type = parts[0];
        String sender = parts[1];
        String receiver = parts[2];
        String text = parts[3];

        return new Message(type, sender, receiver, text);
    }

    @Override
    public String toString() {
        return "Message{"
                + "type=" + type
                + ", sender='" + sender + '\''
                + ", receiver='" + receiver + '\''
                + ", text='" + text + '\''
                + '}';
    }
}
