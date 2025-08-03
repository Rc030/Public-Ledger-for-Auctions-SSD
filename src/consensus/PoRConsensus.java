package consensus;

import ledger.Block;
import ledger.Blockchain;
import ledger.Transaction;
import util.CryptoUtil;

import java.security.KeyPair;
import java.security.PublicKey;
import java.util.List;

public class PoRConsensus implements ConsensusEngine {

    private static final double TRUST_THRESHOLD = 0.7;

    @Override
    public Block generateNextBlock(List<Transaction> txPool, Blockchain blockchain, KeyPair keyPair) throws Exception {
        String previousHash = blockchain.getLatestBlock().getHash();
        Block block = new Block(previousHash, txPool);

        String canonicalData = block.getCanonicalData();
        String signature = CryptoUtil.signData(canonicalData.getBytes(), keyPair.getPrivate());

        block.setSignature(signature);
        block.setPublicKey(CryptoUtil.publicKeyToBase64(keyPair.getPublic()));

        return block;
    }

    @Override
    public boolean validateBlock(Block block, Blockchain blockchain) throws Exception {
        List<Transaction> transactions = block.getTransactions();

        if (transactions == null || transactions.isEmpty()) {
            System.err.println("[CONSENSUS] PoR block rejected: no transactions.");
            return false;
        }

        Transaction first = transactions.get(0);
        double trust = first.getTrustScore();

        if (trust < TRUST_THRESHOLD) {
            System.err.printf("[CONSENSUS] PoR block rejected: trustScore %.2f < threshold %.2f%n", trust, TRUST_THRESHOLD);
            return false;
        }

        try {
            PublicKey pubKey = CryptoUtil.base64ToPublicKey(first.getPublicKey());
            if (!first.verifySignature(pubKey)) {
                System.err.println("[CONSENSUS] PoR block rejected: signature invalid.");
                return false;
            }
        } catch (Exception e) {
            System.err.println("[CONSENSUS] PoR block rejected: failed to parse public key - " + e.getMessage());
            return false;
        }

        if (!block.getPreviousHash().equals(blockchain.getLatestBlock().getHash())) {
            System.err.println("[CONSENSUS] PoR block rejected: previous hash mismatch.");
            return false;
        }

        return true;
    }

    @Override
    public String getName() {
        return "Proof-of-Reputation";
    }
}
