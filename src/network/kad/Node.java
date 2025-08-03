package network.kad;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

public class Node {
    private final KademliaID nodeId;
    private final InetAddress ipAddress;
    private final int port;
    private long lastSeen;
    private double trustScore;

    public Node(KademliaID nodeId, String ip, int port) throws UnknownHostException {
        this.nodeId = nodeId;
        this.ipAddress = InetAddress.getByName(ip);
        this.port = port;
        this.lastSeen = System.currentTimeMillis();
        this.trustScore = 1.0;
    }

    public KademliaID getNodeId() {
        return nodeId;
    }

    public InetAddress getIpAddress() {
        return ipAddress;
    }

    public int getPort() {
        return port;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Node other = (Node) obj;
        return port == other.port &&
                nodeId.equals(other.nodeId) &&
                ipAddress.equals(other.ipAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeId, ipAddress, port);
    }

    @Override
    public String toString() {
        return "Node{" +
                "id=" + nodeId +
                ", ip=" + ipAddress.getHostAddress() +
                ", port=" + port +
                ", trust=" + trustScore +
                '}';
    }
}
