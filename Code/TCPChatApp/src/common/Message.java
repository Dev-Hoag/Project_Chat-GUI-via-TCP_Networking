package common;

public class Message {
    private final String type;
    private final String sender;
    private final String receiver;
    private final String text;

    public Message(String type, String sender, String receiver, String text) {
        this.type = type;
        this.sender = sender;
        this.receiver = receiver;
        this.text = text;
    }

    public String getType() {
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

    public String toProtocolString() {
        return Protocol.build(type, sender, receiver, text);
    }

    public static Message fromProtocolString(String rawMessage) {
        Protocol.ParsedMessage parsedMessage = Protocol.parse(rawMessage);
        return new Message(
                parsedMessage.getCommand(),
                parsedMessage.field(0),
                parsedMessage.field(1),
                parsedMessage.field(2)
        );
    }
}
