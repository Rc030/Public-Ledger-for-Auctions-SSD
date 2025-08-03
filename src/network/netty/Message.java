package network.netty;

public class Message {
    private String type;
    private String senderId;
    private String payload;
    private String senderIp;
    private int senderPort;

    public Message(String type, String senderId, String payload) {
        this.type = type;
        this.senderId = senderId;
        this.payload = payload;
    }

    public String getType() {
        return type;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getPayload() {
        return payload;
    }

    public String getSenderIp() {
        return senderIp;
    }

    public void setSenderIp(String senderIp) {
        this.senderIp = senderIp;
    }

    public int getSenderPort() {
        return senderPort;
    }

    public void setSenderPort(int senderPort) {
        this.senderPort = senderPort;
    }
}
