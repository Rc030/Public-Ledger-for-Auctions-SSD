package network.kad;

import java.util.HashMap;
import java.util.Map;

public class TrustManager {
    private final Map<String, TrustEntry> trustMap = new HashMap<>();
    private final long EXPIRATION_MS = 24 * 60 * 60 * 1000;

    public void recordSuccess(String nodeId) {
        TrustEntry entry = trustMap.computeIfAbsent(nodeId, k -> new TrustEntry());
        entry.success++;
        entry.lastInteraction = System.currentTimeMillis();
        System.out.printf("[REPUTATION] Success recorded for %s | Total: %d\n", nodeId, entry.success);
        System.out.printf("[REPUTATION] Estimated score: %.2f\n", getTrust(nodeId));
    }

    public void recordFailure(String nodeId) {
        TrustEntry entry = trustMap.computeIfAbsent(nodeId, k -> new TrustEntry());
        entry.failure++;
        entry.lastInteraction = System.currentTimeMillis();
        System.out.printf("[REPUTATION] Failure recorded for %s | Total: %d\n", nodeId, entry.failure);
        System.out.printf("[REPUTATION] Estimated score: %.2f\n", getTrust(nodeId));
    }

    public double getTrust(String nodeId) {
        TrustEntry entry = trustMap.get(nodeId);
        if (entry == null) return 0.5;

        long now = System.currentTimeMillis();
        long elapsed = now - entry.lastInteraction;

        if (elapsed > EXPIRATION_MS) {
            System.out.printf("[REPUTATION] Trust expired due to inactivity for %s.\n", nodeId);
            return 0.5;
        }

        double decayFactor = 1.0 - ((double) elapsed / EXPIRATION_MS);
        decayFactor = Math.max(decayFactor, 0.1);
        return entry.getScore() * decayFactor;
    }

    public void setTrust(String nodeId, double score) {
        TrustEntry entry = trustMap.computeIfAbsent(nodeId, k -> new TrustEntry());
        entry.setScore(score);
        entry.lastInteraction = System.currentTimeMillis();
        System.out.printf("[REPUTATION] Manually defined trust: %s → %.2f\n", nodeId, score);
    }

    public void resetTrust() {
        trustMap.clear();
        System.out.println("[REPUTATION] All reputations have been cleared.");
    }
}
