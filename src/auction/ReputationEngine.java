package auction;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ReputationEngine {
    private final Map<String, Double> trustScores = new HashMap<>();
    private final Set<String> processedTransactions = new HashSet<>();

    public void registerSuccess(String peerId) {
        registerSuccess(peerId, 0.05);
    }

    public void registerSuccess(String peerId, double amount) {
        double current = trustScores.getOrDefault(peerId, 0.5);
        double updated = Math.min(1.0, current + amount);
        trustScores.put(peerId, updated);
        System.out.println("[REPUTATION] Success for " + peerId);
        System.out.printf("[REPUTATION] TrustScore: %.2f → %.2f%n", current, updated);
    }

    public void registerFailure(String peerId) {
        registerFailure(peerId, 0.05);
    }

    public void registerFailure(String peerId, double penaltyAmount) {
        System.out.println("[REPUTATION] Entered registerFailure with peerId: " + peerId);
        double previous = trustScores.getOrDefault(peerId, 0.5);
        double updated = Math.max(0.0, previous - penaltyAmount);
        trustScores.put(peerId, updated);
        System.out.printf("[REPUTATION] Reputation updated for %s: %.2f → %.2f%n", peerId, previous, updated);
    }

    public double getTrustScore(String peerId) {
        return trustScores.getOrDefault(peerId, 0.5);
    }

    public boolean isTransactionProcessed(String txId) {
        return processedTransactions.contains(txId);
    }

    public void markTransactionProcessed(String txId) {
        processedTransactions.add(txId);
    }
}
