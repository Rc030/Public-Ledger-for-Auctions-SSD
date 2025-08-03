package network;

import network.kad.KademliaID;
import network.kad.Node;
import network.kad.RoutingTable;
import network.kad.TrustRegistry;

import java.net.UnknownHostException;
import java.util.List;

public class PeerManager {
    private final RoutingTable routingTable;

    public PeerManager(KademliaID localId) {
        this.routingTable = new RoutingTable(localId, 160, 20);
    }

    public boolean addPeer(String nodeIdHex, String ip, int port) {
        try {
            if (ip == null || ip.equals("127.0.0.1") || port < 1 || port >= 65536) {
                System.out.printf("[NETWORK] Ignored peer with invalid IP or port (%s:%d)%n", ip, port);
                return false;
            }

            long sameIpCount = getAllKnownPeers().stream()
                    .filter(n -> n.getIpAddress().getHostAddress().equals(ip))
                    .count();
            if (sameIpCount >= 3) {
                System.out.printf("[NETWORK] Rejected peer %s — too many (%d) peers with same IP %s%n", nodeIdHex, sameIpCount, ip);
                return false;
            }

            double trust = TrustRegistry.trustManager.getTrust(nodeIdHex);
            if (trust < 0.2) {
                System.out.printf("[NETWORK] Rejected peer %s — low trust (%.2f)%n", nodeIdHex, trust);
                return false;
            }

            KademliaID id = new KademliaID(hexToBytes(nodeIdHex));

            if (id.equals(routingTable.getMyId())) {
                System.out.println("[NETWORK] Ignoring self-node during peer addition.");
                return false;
            }

            Node newNode = new Node(id, ip, port);

            if (routingTable.hasConflict(newNode)) {
                System.out.printf("[NETWORK] Rejected peer %s — same IP:PORT as existing node with different ID%n", nodeIdHex);
                return false;
            }

            if (!routingTable.containsExact(newNode)) {
                routingTable.update(newNode);
                return true;
            }

            return false;

        } catch (UnknownHostException e) {
            System.err.printf("[NETWORK] Invalid IP while adding peer: %s (%s)%n", ip, e.getMessage());
        } catch (Exception e) {
            System.err.printf("[NETWORK] Unexpected error adding peer %s:%d — %s%n", ip, port, e.getMessage());
        }

        return false;
    }

    public List<Node> findClosestPeers(KademliaID target, int count) {
        return routingTable.findClosest(target, count);
    }

    public List<Node> getAllKnownPeers() {
        return routingTable.getAllNodes();
    }

    public Node getNodeById(String nodeId) {
        return routingTable.getAllNodes().stream()
                .filter(node -> node.getNodeId().toString().equals(nodeId))
                .findFirst()
                .orElse(null);
    }

    private byte[] hexToBytes(String hex) {
        if (hex.length() < 40) {
            hex = String.format("%1$-" + 40 + "s", hex).replace(' ', '0');
        } else if (hex.length() > 40) {
            hex = hex.substring(0, 40);
        }

        byte[] data = new byte[20];
        for (int i = 0; i < 40; i += 2) {
            data[i / 2] = (byte) (
                    (Character.digit(hex.charAt(i), 16) << 4)
                            + Character.digit(hex.charAt(i + 1), 16)
            );
        }
        return data;
    }
}
