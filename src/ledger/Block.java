package ledger;

import com.google.gson.Gson;

import java.util.List;

public class Block {
    private String previousHash;
    private String hash;
    private long timestamp;
    private int nonce;
    private List<Transaction> transactions;
    private String signature;
    private String publicKey;

    public Block(String previousHash, List<Transaction> transactions) {
        this.previousHash = previousHash;
        this.transactions = transactions;
        this.timestamp = System.currentTimeMillis();
        this.hash = calculateHash();
    }

    public String calculateHash() {
        return HashUtil.sha256(getCanonicalData());
    }

    public void mineBlock(int difficulty) {
        String target = new String(new char[difficulty]).replace('\0', '0');
        while (!hash.substring(0, difficulty).equals(target)) {
            nonce++;
            hash = calculateHash();
        }
    }

    public String getCanonicalData() {
        Gson gson = new Gson();
        String txData = gson.toJson(transactions);
        return previousHash + timestamp + nonce + txData;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public String getHash() {
        return hash;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getNonce() {
        return nonce;
    }

    public List<Transaction> getTransactions() {
        return transactions;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    @Override
    public String toString() {
        return "Block{" +
                "previousHash='" + previousHash + '\'' +
                ", hash='" + hash + '\'' +
                ", timestamp=" + timestamp +
                ", nonce=" + nonce +
                ", transactions=" + transactions +
                ", signature='" + signature + '\'' +
                ", publicKey='" + publicKey + '\'' +
                '}';
    }
}
