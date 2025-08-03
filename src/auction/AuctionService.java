package auction;

public class AuctionService {

    public static class CreateAuctionPayload {
        public String auctionId;
        public String itemName;
        public double minBid;
        public String sellerId;
    }

}
