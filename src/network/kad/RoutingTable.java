package network.kad;

import java.util.*;
import java.util.stream.Collectors;

public class RoutingTable {
    private final List<Set<Node>> buckets;
    private final KademliaID myId;
    private final int k;
    private static final boolean DEBUG = false;

    public RoutingTable(KademliaID myId, int bucketCount, int k) {
        this.myId = myId;
        this.k = k;
        this.buckets = new ArrayList<>(bucketCount);
        for (int i = 0; i < bucketCount; i++) {
            buckets.add(new LinkedHashSet<>());
        }
    }

    public void update(Node node) {
        if (node == null || node.getNodeId() == null || node.getIpAddress() == null || node.getPort() <= 0) {
            System.out.println("[ROUTING] Ignoring invalid node on update: " + node);
            return;
        }

        String nodeId = node.getNodeId().toString();
        if (nodeId.equals(myId.toString())) return;

        for (Set<Node> bucket : buckets) {
            for (Node existing : bucket) {
                if (existing.getIpAddress().equals(node.getIpAddress()) &&
                        existing.getPort() == node.getPort() &&
                        !existing.getNodeId().equals(node.getNodeId())) {
                    System.out.printf("[ROUTING] Rejected node %s — same IP:PORT as %s with different ID\n",
                            getShortId(nodeId), getShortId(existing.getNodeId().toString()));
                    return;
                }
            }
        }

        int bucketIndex = myId.getDistance(node.getNodeId()).bitLength() - 1;
        if (bucketIndex < 0 || bucketIndex >= buckets.size()) return;

        Set<Node> bucket = buckets.get(bucketIndex);
        if (bucket.contains(node)) {
            bucket.remove(node);
            bucket.add(node);
            return;
        }

        if (bucket.size() >= k) {
            Iterator<Node> it = bucket.iterator();
            Node removed = it.next();
            it.remove();
            System.out.printf("[ROUTING] Bucket %d full. Removed: %s\n", bucketIndex, getShortId(removed.getNodeId().toString()));
        }

        bucket.add(node);
        System.out.printf("[ROUTING] New peer %s added to bucket %d\n", getShortId(nodeId), bucketIndex);
        System.out.printf("[ROUTING] Bucket %d now contains %d peer(s):\n", bucketIndex, bucket.size());
        for (Node n : bucket) {
            System.out.printf(" - %s | IP: %s | Port: %d\n", getShortId(n.getNodeId().toString()), n.getIpAddress().getHostAddress(), n.getPort());
        }
    }

    public List<Node> findClosest(KademliaID targetId, int count) {
        if (DEBUG) {
            System.out.println("[DEBUG] FIND_CLOSEST - Target ID: " + targetId);
            for (Node node : getAllNodes()) {
                System.out.printf("[DEBUG] Peer %s | IP: %s | Port: %d\n",
                        node.getNodeId(), node.getIpAddress().getHostAddress(), node.getPort());
            }
        }

        double balanceFactor = 0.65;

        PriorityQueue<Node> heap = new PriorityQueue<>(
                Comparator.comparingDouble(n -> {
                    double xorDistance = n.getNodeId().getDistance(targetId).doubleValue();
                    double trust = TrustRegistry.trustManager.getTrust(n.getNodeId().toString());
                    trust = Math.max(trust, 0.01);
                    return balanceFactor * xorDistance + (1 - balanceFactor) * (1.0 / trust);
                })
        );

        for (Set<Node> bucket : buckets) {
            heap.addAll(bucket);
        }

        List<Node> closest = new ArrayList<>();
        while (!heap.isEmpty() && closest.size() < count) {
            closest.add(heap.poll());
        }

        if (DEBUG) {
            System.out.println("[DEBUG] Peer selection based on S/Kademlia:");
            for (Node node : closest) {
                double xorDist = node.getNodeId().getDistance(targetId).doubleValue();
                double trust = TrustRegistry.trustManager.getTrust(node.getNodeId().toString());
                trust = Math.max(trust, 0.01);
                double score = balanceFactor * xorDist + (1 - balanceFactor) * (1.0 / trust);
                System.out.printf("[DEBUG] Peer %s | XOR: %.4f | Trust: %.2f | S/Kad: %.4f\n",
                        node.getNodeId(), xorDist, trust, score);
            }
        }

        return closest;
    }

    public List<Node> getAllNodes() {
        return buckets.stream().flatMap(Collection::stream).collect(Collectors.toList());
    }

    public boolean containsExact(Node node) {
        return getAllNodes().stream().anyMatch(n ->
                n.getIpAddress().equals(node.getIpAddress()) &&
                        n.getPort() == node.getPort() &&
                        n.getNodeId().equals(node.getNodeId()));
    }

    public boolean hasConflict(Node node) {
        return getAllNodes().stream().anyMatch(n ->
                n.getIpAddress().equals(node.getIpAddress()) &&
                        n.getPort() == node.getPort() &&
                        !n.getNodeId().equals(node.getNodeId()));
    }

    public Node findNodeById(String nodeId) {
        return getAllNodes().stream()
                .filter(n -> n.getNodeId().toString().equals(nodeId))
                .findFirst()
                .orElse(null);
    }

    public KademliaID getMyId() {
        return myId;
    }

    private String getShortId(String fullId) {
        return fullId.substring(0, 8) + "..." + fullId.substring(fullId.length() - 4);
    }
}
