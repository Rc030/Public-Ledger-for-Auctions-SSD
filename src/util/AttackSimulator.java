package util;

import network.NetworkEngine;
import network.kad.TrustRegistry;

public class AttackSimulator {

    public static void simulateSybilAttack(NetworkEngine network) {
        System.out.println("[ATTACK] Starting Sybil attack simulation...");

        for (int i = 0; i < 10; i++) {
            String fakeId = "SYBIL_" + i;
            String fakeIp = "10.0.0." + (100 + i);
            int fakePort = 4000 + i;

            TrustRegistry.trustManager.setTrust(fakeId, 0.1);

            boolean added = network.getPeerManager().addPeer(fakeId, fakeIp, fakePort);
            if (added) {
                System.out.printf("[ATTACK] Sybil node inserted: %s (%s:%d)%n", fakeId, fakeIp, fakePort);
            } else {
                System.out.printf("[ATTACK] Sybil node rejected: %s (%s:%d)%n", fakeId, fakeIp, fakePort);
            }
        }
    }

    public static void simulateEclipseAttack(NetworkEngine network) {
        System.out.println("[ATTACK] Starting Eclipse attack simulation...");

        String attackerIp = "192.168.50.50";
        for (int i = 0; i < 10; i++) {
            String id = "ECLIPSE_" + i;
            int port = 3000 + i;

            boolean added = network.getPeerManager().addPeer(id, attackerIp, port);
            if (added) {
                System.out.printf("[ATTACK] Eclipse node added: %s (port %d)%n", id, port);
            } else {
                System.out.printf("[ATTACK] Eclipse node rejected: %s (port %d)%n", id, port);
            }
        }
    }
}
