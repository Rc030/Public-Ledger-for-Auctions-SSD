package auction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Auction {
    private String auctionId;
    private String itemName;
    private double minBid;
    private String sellerId;
    private List<Bid> bids;
    private boolean finished = false;

    public Auction(String auctionId, String itemName, double minBid, String sellerId) {
        this.auctionId = auctionId;
        this.itemName = itemName;
        this.minBid = minBid;
        this.sellerId = sellerId;
        this.bids = new ArrayList<>();
    }

    public String getAuctionId() {
        return auctionId;
    }

    public String getItemName() {
        return itemName;
    }

    public double getMinBid() {
        return minBid;
    }

    public String getSellerId() {
        return sellerId;
    }

    public List<Bid> getBids() {
        return bids;
    }

    public boolean isFinished() {
        return finished;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
    }

    public void addBid(Bid bid) {
        bids.add(bid);
    }

    public Bid getWinningBid() {
        return bids.stream()
                .max(Comparator.comparingDouble(Bid::getAmount))
                .orElse(null);
    }
}
