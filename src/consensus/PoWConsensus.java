package consensus;

import ledger.Block;
import ledger.Blockchain;
import ledger.Transaction;
import util.CryptoUtil;

import java.security.KeyPair;
import java.util.List;

public class PoWConsensus implements ConsensusEngine {

    private static final int DIFFICULTY = 4;

    @Override
    public Block generateNextBlock(List<Transaction> txPool, Blockchain blockchain, KeyPair keyPair) throws Exception {
        String previousHash = blockchain.getLatestBlock().getHash();
        Block block = new Block(previousHash, txPool);
        block.mineBlock(DIFFICULTY);

        String canonicalData = block.getCanonicalData();
        String signature = CryptoUtil.signData(canonicalData.getBytes(), keyPair.getPrivate());

        block.setSignature(signature);
        block.setPublicKey(CryptoUtil.publicKeyToBase64(keyPair.getPublic()));

        return block;
    }

    @Override
    public boolean validateBlock(Block block, Blockchain blockchain) throws Exception {
        String calculatedHash = block.calculateHash();
        String target = "0".repeat(DIFFICULTY);

        if (block.getTransactions().isEmpty()) {
            System.err.println("[CONSENSUS] PoW block rejected: no transactions.");
            return false;
        }

        boolean valid = block.getHash().equals(calculatedHash)
                && block.getHash().startsWith(target)
                && block.getPreviousHash().equals(blockchain.getLatestBlock().getHash());

        if (!valid) {
            System.err.println("[CONSENSUS] PoW block failed validation.");
        }

        return valid;
    }

    @Override
    public String getName() {
        return "Proof-of-Work";
    }
}
