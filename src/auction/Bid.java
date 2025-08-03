package auction;

import ledger.Transaction;

public class Bid {
    private String auctionId;
    private String bidderId;
    private double amount;
    private long timestamp;
    private double trustScore;
    private Transaction transaction;

    public Bid(String auctionId, double amount, String bidderId, long timestamp, double trustScore) {
        this.auctionId = auctionId;
        this.amount = amount;
        this.bidderId = bidderId;
        this.timestamp = timestamp;
        this.trustScore = trustScore;
    }

    public String getAuctionId() {
        return auctionId;
    }

    public String getBidderId() {
        return bidderId;
    }

    public double getAmount() {
        return amount;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public double getTrustScore() {
        return trustScore;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public void setTransaction(Transaction transaction) {
        this.transaction = transaction;
    }

    @Override
    public String toString() {
        return "Bid{" +
                "auctionId='" + auctionId + '\'' +
                ", bidderId='" + bidderId + '\'' +
                ", amount=" + amount +
                ", timestamp=" + timestamp +
                ", trustScore=" + trustScore +
                '}';
    }
}
