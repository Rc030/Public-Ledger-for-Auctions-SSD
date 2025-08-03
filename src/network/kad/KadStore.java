package network.kad;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class KadStore {
    private final ConcurrentHashMap<String, String> store = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<Node>> subscriptions = new ConcurrentHashMap<>();

    public void put(String key, String value) {
        store.put(key, value);
    }

    public String get(String key) {
        return store.get(key);
    }

    public void subscribe(String topic, Node node) {
        subscriptions.computeIfAbsent(topic, k -> ConcurrentHashMap.newKeySet()).add(node);
    }

    public Set<Node> getSubscribers(String topic) {
        return subscriptions.getOrDefault(topic, Collections.emptySet());
    }

    public void unsubscribe(String topic, Node node) {
        Set<Node> currentSubscribers = subscriptions.get(topic);
        System.out.println("[PUBSUB] Subscribers before removal for topic [" + topic + "]: " + currentSubscribers);

        if (currentSubscribers != null) {
            Node toRemove = null;
            for (Node n : currentSubscribers) {
                System.out.println("[PUBSUB] Comparing nodes:");
                System.out.println("[PUBSUB] Current node: " + n);
                System.out.println("[PUBSUB] Target node : " + node);
                System.out.println("[PUBSUB] nodeId.equals: " + n.getNodeId().equals(node.getNodeId()));

                if (n.getNodeId().equals(node.getNodeId())) {
                    toRemove = n;
                    System.out.println("[PUBSUB] Found matching node to remove: " + n);
                    break;
                } else {
                    System.out.println("[PUBSUB] No match for nodeId: " + n.getNodeId() + " vs " + node.getNodeId());
                }
            }

            if (toRemove != null) {
                currentSubscribers.remove(toRemove);
                System.out.printf("[PUBSUB] Peer removed from topic [%s]: %s\n", topic, toRemove);
            } else {
                System.out.println("[PUBSUB] No matching node found to remove for topic [" + topic + "]");
            }
        } else {
            System.out.println("[PUBSUB] No subscribers found for topic [" + topic + "]");
        }

        System.out.println("[PUBSUB] Subscribers after removal for topic [" + topic + "]: " + subscriptions.get(topic));
    }
}
