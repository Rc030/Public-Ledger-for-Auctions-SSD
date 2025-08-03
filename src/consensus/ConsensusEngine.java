package consensus;

import ledger.Block;
import ledger.Blockchain;
import ledger.Transaction;

import java.security.KeyPair;
import java.util.List;

public interface ConsensusEngine {
    Block generateNextBlock(List<Transaction> txPool, Blockchain blockchain, KeyPair keyPair) throws Exception;
    boolean validateBlock(Block block, Blockchain blockchain) throws Exception;
    String getName();
}
