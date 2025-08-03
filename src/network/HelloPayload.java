package network;

public class HelloPayload {
    public String nodeId;
    public String ip;
    public int port;

    public HelloPayload(String nodeId, String ip, int port) {
        this.nodeId = nodeId;
        this.ip = ip;
        this.port = port;
    }
}
