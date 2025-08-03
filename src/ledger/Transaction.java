package ledger;

import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;

public class Transaction {
    private String senderId;
    private String payload;
    private long timestamp;
    private String signature;
    private double trustScore;
    private String publicKey;

    public Transaction(String senderId, String payload, long timestamp, String signature) {
        this.senderId = senderId;
        this.payload = payload;
        this.timestamp = timestamp;
        this.signature = signature;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getPayload() {
        return payload;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getSignature() {
        return signature;
    }

    public double getTrustScore() {
        return trustScore;
    }

    public void setTrustScore(double trustScore) {
        this.trustScore = trustScore;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public boolean verifySignature(PublicKey publicKey) {
        try {
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(publicKey);
            String message = senderId + payload + timestamp;
            sig.update(message.getBytes());
            byte[] signatureBytes = Base64.getDecoder().decode(signature);
            return sig.verify(signatureBytes);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "senderId='" + senderId + '\'' +
                ", payload='" + payload + '\'' +
                ", timestamp=" + timestamp +
                ", signature='" + signature + '\'' +
                ", trustScore=" + trustScore +
                ", publicKey='" + publicKey + '\'' +
                '}';
    }
}
