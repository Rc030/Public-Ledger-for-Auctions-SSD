package ledger;

import consensus.ConsensusEngine;
import consensus.PoRConsensus;
import consensus.PoWConsensus;
import network.NetworkEngine;
import util.CryptoUtil;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

public class Blockchain {
    private List<Block> blockchain;
    private int difficulty = 4;

    public Blockchain() {
        blockchain = new ArrayList<>();
        Block genesis = new Block("0", new ArrayList<>());
        genesis.mineBlock(difficulty);
        blockchain.add(genesis);
    }

    public boolean containsBlock(String hash) {
        return blockchain.stream().anyMatch(b -> b.getHash().equals(hash));
    }

    public boolean addBlock(Block block, ConsensusEngine consensusEngine, NetworkEngine networkEngine) {
        System.out.println("[BLOCK] Attempting to add a new block...");

        Block latestBlock = getLatestBlock();

        if (!latestBlock.getHash().equals(block.getPreviousHash())) {
            System.err.println("[BLOCK] Invalid block: previousHash does not match.");
            return false;
        }

        if (!block.getHash().equals(block.calculateHash())) {
            System.err.println("[BLOCK] Invalid block: incorrect hash.");
            return false;
        }

        try {
            PublicKey pubKey = CryptoUtil.base64ToPublicKey(block.getPublicKey());
            byte[] data = block.getCanonicalData().getBytes();
            boolean valid = CryptoUtil.verifySignature(data, block.getSignature(), pubKey);
            if (!valid) {
                System.err.println("[BLOCK] Block signature invalid.");
                return false;
            } else {
                System.out.println("[BLOCK] Block signature valid for block hash: " + block.getHash());
            }
        } catch (Exception e) {
            System.err.println("[BLOCK] Error verifying block signature: " + e.getMessage());
            return false;
        }

        for (Transaction tx : block.getTransactions()) {
            try {
                PublicKey txPubKey = CryptoUtil.base64ToPublicKey(tx.getPublicKey());
                if (!tx.verifySignature(txPubKey)) {
                    System.err.println("[TRANSACTION] Transaction signature invalid for tx from " + tx.getSenderId());
                    return false;
                } else {
                    System.out.println("[TRANSACTION] Transaction signature valid for tx from " + tx.getSenderId());
                }
            } catch (Exception e) {
                System.err.println("[TRANSACTION] Error verifying transaction signature: " + e.getMessage());
                return false;
            }
        }

        try {
            if (!consensusEngine.validateBlock(block, this)) {
                System.err.println("[CONSENSUS] Consensus validation failed: " + consensusEngine.getName());
                return false;
            }
        } catch (Exception e) {
            System.err.println("[CONSENSUS] Error during block validation: " + e.getMessage());
            return false;
        }

        if (block.getTransactions().isEmpty()) {
            System.err.println("[BLOCK] Block rejected: no transactions.");
            return false;
        }

        blockchain.add(block);
        BlockchainStorage.saveBlockchain(blockchain);

        System.out.println("[BLOCK] Block successfully added. Transactions:");
        for (Transaction tx : block.getTransactions()) {
            System.out.println("[TRANSACTION] " + tx);
        }

        for (Transaction tx : block.getTransactions()) {
            String txId = tx.getSenderId() + tx.getPayload() + tx.getTimestamp();
            if (!networkEngine.getReputationEngine().isTransactionProcessed(txId)) {
                networkEngine.getReputationEngine().registerSuccess(tx.getSenderId());
                networkEngine.getReputationEngine().markTransactionProcessed(txId);
                double updatedScore = networkEngine.getReputationEngine().getTrustScore(tx.getSenderId());
                System.out.println("[REPUTATION] Reputation updated: " + tx.getSenderId() + " → " + updatedScore + " (" + consensusEngine.getName() + ")");

                if (consensusEngine instanceof PoWConsensus && updatedScore >= 0.7) {
                    networkEngine.setConsensusEngine(new PoRConsensus());
                    System.out.println("[CONSENSUS] Automatic switch: PoW → PoR");
                } else if (consensusEngine instanceof PoRConsensus && updatedScore < 0.7) {
                    networkEngine.setConsensusEngine(new PoWConsensus());
                    System.out.println("[CONSENSUS] Automatic switch: PoR → PoW");
                }
            } else {
                System.out.println("[REPUTATION] Reputation already updated for this transaction: " + txId);
            }
        }

        return true;
    }

    public Block getLatestBlock() {
        return blockchain.get(blockchain.size() - 1);
    }

    public List<Block> getChain() {
        return blockchain;
    }

    public void loadFromReceivedChain(List<Block> receivedChain) {
        if (receivedChain != null && !receivedChain.isEmpty()) {
            this.blockchain = new ArrayList<>(receivedChain);
            System.out.println("[BLOCKCHAIN] Blockchain updated from the network. Blocks: " + blockchain.size());
            BlockchainStorage.saveBlockchain(blockchain);
        } else {
            System.err.println("[BLOCKCHAIN] Received blockchain is empty or null.");
        }
    }

    public boolean containsTransaction(Transaction tx) {
        return blockchain.stream()
                .flatMap(block -> block.getTransactions().stream())
                .anyMatch(existingTx ->
                        existingTx.getSenderId().equals(tx.getSenderId()) &&
                                existingTx.getPayload().equals(tx.getPayload()) &&
                                existingTx.getTimestamp() == tx.getTimestamp()
                );
    }

    @Override
    public String toString() {
        return "Blockchain{" +
                "chain=" + blockchain +
                '}';
    }
}
