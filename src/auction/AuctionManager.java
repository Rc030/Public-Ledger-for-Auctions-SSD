package auction;

import consensus.PoRConsensus;
import consensus.PoWConsensus;
import ledger.Block;
import ledger.Blockchain;
import ledger.Transaction;
import network.NetworkEngine;
import network.kad.KademliaID;
import network.netty.Message;
import util.KeyStoreUtil;
import com.google.gson.Gson;

import java.security.KeyPair;
import java.security.Signature;
import java.util.*;
import java.util.stream.Collectors;

public class AuctionManager {
    private final Blockchain blockchain;
    private final List<Auction> auctions = new ArrayList<>();
    private final NetworkEngine networkEngine;
    private String consensusType;

    public AuctionManager(Blockchain blockchain, NetworkEngine networkEngine) {
        this.blockchain = blockchain;
        this.networkEngine = networkEngine;
    }

    public void setConsensusType(String consensusType) {
        this.consensusType = consensusType;
    }

    public Auction createAuction(String auctionId, String itemName, double minBid, String sellerId) {
        Auction auction = new Auction(auctionId, itemName, minBid, sellerId);
        auctions.add(auction);
        broadcastAuction(auction);

        if (networkEngine != null && sellerId != null) {
            double bonus = (networkEngine.getConsensusEngine() instanceof PoRConsensus) ? 0.05 : 0.02;
            networkEngine.getReputationEngine().registerSuccess(sellerId, bonus);
            System.out.printf("[REPUTATION] Seller %s reputation increased by %.2f for creating auction \"%s\".%n", sellerId, bonus, itemName);
        }

        return auction;
    }

    public void createAuction(Auction auction) {
        auctions.add(auction);
    }

    public boolean placeBid(Bid bid) {
        Auction auction = auctions.stream()
                .filter(a -> a.getAuctionId().equals(bid.getAuctionId()))
                .findFirst()
                .orElse(null);

        if (auction == null) {
            System.err.printf("[AUCTION] Bid rejected: auction not found (ID: %s)%n", bid.getAuctionId());

            if (networkEngine != null && bid.getBidderId() != null) {
                double penalty = (networkEngine.getConsensusEngine() instanceof PoRConsensus) ? 0.10 : 0.03;
                networkEngine.getReputationEngine().registerFailure(bid.getBidderId(), penalty);
                System.out.printf("[REPUTATION] Penalty %.2f applied to %s for bidding on non-existent auction.%n", penalty, bid.getBidderId());
            }

            return false;
        }

        if (auction.isFinished()) {
            System.err.printf("[AUCTION] Bid rejected: auction is closed (ID: %s)%n", bid.getAuctionId());

            if (networkEngine != null && bid.getBidderId() != null) {
                double penalty = (networkEngine.getConsensusEngine() instanceof PoRConsensus) ? 0.08 : 0.02;
                networkEngine.getReputationEngine().registerFailure(bid.getBidderId(), penalty);
                System.out.printf("[REPUTATION] Penalty %.2f applied to %s for bidding on closed auction.%n", penalty, bid.getBidderId());
            }

            return false;
        }

        double highestBid = auction.getBids().stream()
                .mapToDouble(Bid::getAmount)
                .max()
                .orElse(auction.getMinBid());

        if (bid.getAmount() <= highestBid) {
            System.err.printf("[AUCTION] Bid rejected: amount %.2f is not higher than current highest bid (%.2f) for auction %s%n", bid.getAmount(), highestBid, bid.getAuctionId());

            if (networkEngine != null && bid.getBidderId() != null) {
                double penalty = (networkEngine.getConsensusEngine() instanceof PoRConsensus) ? 0.10 : 0.02;
                networkEngine.getReputationEngine().registerFailure(bid.getBidderId(), penalty);
                System.out.printf("[REPUTATION] Penalty %.2f applied to %s for bidding below the highest bid.%n", penalty, bid.getBidderId());
            }

            return false;
        }

        auction.addBid(bid);
        System.out.printf("[AUCTION] Bid placed: %.2f for auction %s | Bidder TrustScore: %.2f%n", bid.getAmount(), bid.getAuctionId(), bid.getTrustScore());

        double currentScore = networkEngine.getReputationEngine().getTrustScore(bid.getBidderId());
        System.out.printf("[REPUTATION] Current TrustScore of bidder %s: %.2f%n", bid.getBidderId(), currentScore);
        return true;
    }

    public boolean closeAuction(String auctionId) {
        Auction auction = auctions.stream()
                .filter(a -> a.getAuctionId().equals(auctionId))
                .findFirst()
                .orElse(null);

        if (auction == null) {
            System.err.printf("[AUCTION] Close failed: auction not found (ID: %s)%n", auctionId);
            return false;
        }

        if (auction.isFinished()) {
            System.out.printf("[AUCTION] Auction already closed: %s%n", auctionId);
            return false;
        }

        if (!auction.getSellerId().equals(networkEngine.getLocalNodeId())) {
            System.out.println("[AUCTION] Only the auction creator can close it locally.");
            return false;
        }

        System.out.println("[AUCTION] This node is the auction creator. Attempting to mine the block...");

        auction.setFinished(true);
        broadcastAuctionClosure(auctionId);

        try {
            KeyPair keyPair = KeyStoreUtil.loadOrCreateKeyPair(networkEngine.getLocalNodeId());
            closeAuction(auction, keyPair);
        } catch (Exception e) {
            System.err.println("[KEYSTORE] Error retrieving or generating the user's key.");
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public void closeAuction(Auction auction, KeyPair keyPair) {
        Bid winningBid = auction.getWinningBid();

        if (winningBid == null) {
            System.out.println("[AUCTION] No bid received for this auction. Nothing will be recorded in the blockchain.");
            return;
        }

        try {
            String senderId = winningBid.getBidderId();
            String payload = String.format("{\"auctionId\":\"%s\",\"amount\":%.2f,\"trustScore\":%.2f}", auction.getAuctionId(), winningBid.getAmount(), winningBid.getTrustScore());
            long timestamp = System.currentTimeMillis();

            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(keyPair.getPrivate());
            signer.update(payload.getBytes());
            String signature = Base64.getEncoder().encodeToString(signer.sign());

            Transaction tx = new Transaction(senderId, payload, timestamp, signature);
            tx.setPublicKey(util.CryptoUtil.publicKeyToBase64(keyPair.getPublic()));
            tx.setTrustScore(winningBid.getTrustScore());

            List<Transaction> transactions = new ArrayList<>();
            transactions.add(tx);

            double trustScore = networkEngine.getReputationEngine().getTrustScore(senderId);
            if (trustScore >= 0.7 && !(networkEngine.getConsensusEngine() instanceof PoRConsensus)) {
                networkEngine.setConsensusEngine(new PoRConsensus());
                System.out.println("[CONSENSUS] Auto-switch: trust >= 0.7 — switched to PoR.");
            } else if (trustScore < 0.7 && !(networkEngine.getConsensusEngine() instanceof PoWConsensus)) {
                networkEngine.setConsensusEngine(new PoWConsensus());
                System.out.println("[CONSENSUS] Auto-switch: trust < 0.7 — using PoW.");
            }

            Block block = networkEngine.getConsensusEngine().generateNextBlock(transactions, blockchain, keyPair);

            String latestHash = blockchain.getLatestBlock().getHash();
            if (!block.getPreviousHash().equals(latestHash)) {
                System.err.println("[BLOCKCHAIN] Warning: Detected updated blockchain during mining. Re-mining block with new previousHash...");
                block = new Block(latestHash, transactions);
                if (networkEngine.getConsensusEngine() instanceof PoWConsensus) {
                    ((PoWConsensus) networkEngine.getConsensusEngine()).generateNextBlock(transactions, blockchain, keyPair);
                } else {
                    String blockData = latestHash + block.getTimestamp() + block.getNonce() + transactions.toString();
                    String signature1 = util.CryptoUtil.signData(blockData.getBytes(), keyPair.getPrivate());
                    block.setSignature(signature1);
                    block.setPublicKey(util.CryptoUtil.publicKeyToBase64(keyPair.getPublic()));
                }
            }

            boolean success = blockchain.addBlock(block, networkEngine.getConsensusEngine(), networkEngine);
            if (success) {
                System.out.println("[BLOCKCHAIN] Block accepted, applying reputation reward.");
                networkEngine.getReputationEngine().registerSuccess(senderId);
                networkEngine.broadcastBlock(block);
                System.out.printf("[BLOCKCHAIN] Block mined and added with %s.%n", networkEngine.getConsensusEngine().getName());
            } else {
                System.err.println("[BLOCKCHAIN] Failed to add block.");
                if (networkEngine.getConsensusEngine() instanceof PoRConsensus) {
                    System.out.printf("[REPUTATION] Penalizing local reputation of %s for rejected PoR block.%n", senderId);
                    networkEngine.getReputationEngine().registerFailure(senderId);
                }
            }

        } catch (Exception e) {
            System.err.printf("[BLOCKCHAIN] Error signing or adding transaction: %s%n", e.getMessage());
            e.printStackTrace();
        }
    }

    public void broadcastBid(Bid bid) {
        if (!placeBid(bid)) {
            System.out.println("[AUCTION] Bid rejected. It will not be propagated to the network.");
            return;
        }

        try {
            String payload = String.format("AuctionID:%s;Amount:%.2f;TrustScore:%.2f", bid.getAuctionId(), bid.getAmount(), bid.getTrustScore());
            long timestamp = bid.getTimestamp();
            String message = bid.getBidderId() + payload + timestamp;
            String signature = util.CryptoUtil.signData(message.getBytes(), networkEngine.getKeyPair().getPrivate());

            Transaction tx = new Transaction(bid.getBidderId(), payload, timestamp, signature);
            tx.setPublicKey(networkEngine.getPublicKeyBase64());
            tx.setTrustScore(bid.getTrustScore());
            bid.setTransaction(tx);
            networkEngine.broadcastTransaction(tx);

            System.out.println("[TRANSACTION] Bid transaction created and transmitted.");
        } catch (Exception e) {
            System.err.println("[TRANSACTION] Error generating or transmitting the bid transaction: " + e.getMessage());
        }

        Gson gson = new Gson();
        String json = gson.toJson(bid);
        Message message = new Message("BID", networkEngine.getLocalNodeId(), json);

        KademliaID target = new KademliaID(bid.getAuctionId());
        List<network.kad.Node> peers = networkEngine.getPeerManager().findClosestPeers(target, 10);

        if (peers.isEmpty()) {
            System.out.println("[NETWORK] No peers returned by S/Kademlia. Using full broadcast.");
            networkEngine.broadcastMessage(message);
            return;
        }

        System.out.println("[NETWORK] Sending bid to the closest peers:");
        for (network.kad.Node peer : peers) {
            String ip = peer.getIpAddress().getHostAddress();
            int port = peer.getPort();
            System.out.printf("[NETWORK] Sending bid to %s:%d (%s)%n", ip, port, peer.getNodeId());
            networkEngine.sendMessage(ip, port, message);
        }

        System.out.println("[NETWORK] Bid propagated via S/Kademlia.");
    }

    private void broadcastAuction(Auction auction) {
        Gson gson = new Gson();
        AuctionService.CreateAuctionPayload payload = new AuctionService.CreateAuctionPayload();
        payload.auctionId = auction.getAuctionId();
        payload.itemName = auction.getItemName();
        payload.minBid = auction.getMinBid();
        payload.sellerId = auction.getSellerId();

        String json = gson.toJson(payload);
        Message message = new Message("CREATE_AUCTION", payload.sellerId, json);
        networkEngine.broadcastMessage(message);
    }

    private void broadcastAuctionClosure(String auctionId) {
        Gson gson = new Gson();
        String json = gson.toJson(auctionId);
        Message message = new Message("CLOSE_AUCTION", "system", json);
        networkEngine.broadcastMessage(message);
    }

    public void sendAllAuctionsToNode(network.kad.Node node) {
        Gson gson = new Gson();
        for (Auction auction : auctions) {
            AuctionService.CreateAuctionPayload payload = new AuctionService.CreateAuctionPayload();
            payload.auctionId = auction.getAuctionId();
            payload.itemName = auction.getItemName();
            payload.minBid = auction.getMinBid();
            payload.sellerId = auction.getSellerId();

            String json = gson.toJson(payload);
            Message msg = new Message("CREATE_AUCTION", auction.getSellerId(), json);

            String ip = node.getIpAddress().getHostAddress();
            int port = node.getPort();
            networkEngine.sendMessage(ip, port, msg);
        }
    }

    public void viewBidsAndWinner(String auctionId) {
        Auction auction = auctions.stream()
                .filter(a -> a.getAuctionId().equals(auctionId))
                .findFirst()
                .orElse(null);

        if (auction == null) {
            System.err.println("[AUCTION] Auction not found.");
            return;
        }

        System.out.printf("[AUCTION] Confirmed bids on the blockchain for auction %s:%n", auctionId);

        List<Bid> validBids = new ArrayList<>();

        for (Block block : blockchain.getChain()) {
            for (Transaction tx : block.getTransactions()) {
                if (tx.getPayload().contains("AuctionID:" + auctionId)) {
                    try {
                        String[] parts = tx.getPayload().split(";");
                        double amount = Double.parseDouble(parts[1].split(":")[1].replace(",", "."));
                        double trust = Double.parseDouble(parts[2].split(":")[1].replace(",", "."));
                        String sender = tx.getSenderId();
                        validBids.add(new Bid(auctionId, amount, sender, tx.getTimestamp(), trust));
                        System.out.printf("[BLOCKCHAIN] Validated bid - %s -> %.2f (Trust: %.2f)%n", sender, amount, trust);
                    } catch (Exception e) {
                        System.err.println("[TRANSACTION] Error interpreting transaction: " + e.getMessage());
                    }
                }
            }
        }

        if (validBids.isEmpty()) {
            System.out.println("[AUCTION] No bids confirmed via consensus.");
        } else {
            Bid highest = validBids.stream()
                    .max(Comparator.comparingDouble(Bid::getAmount))
                    .orElse(null);
            if (highest != null) {
                System.out.printf("[WINNER] %s with the bid of %.2f%n", highest.getBidderId(), highest.getAmount());
            }
        }

        List<Bid> localOnly = auction.getBids().stream()
                .filter(b -> validBids.stream().noneMatch(v -> v.getAmount() == b.getAmount() && v.getBidderId().equals(b.getBidderId())))
                .toList();

        if (!localOnly.isEmpty()) {
            System.out.println("[AUCTION] Local bids not validated (by PoR or not mined):");
            for (Bid b : localOnly) {
                System.out.printf("[AUCTION] Local only - %s -> %.2f (Trust: %.2f)%n", b.getBidderId(), b.getAmount(), b.getTrustScore());
            }
        }
    }

    public List<Auction> getAllAuctions() {
        return auctions;
    }

    public List<Auction> getOpenAuctions() {
        return auctions.stream()
                .filter(a -> !a.isFinished())
                .collect(Collectors.toList());
    }

    public NetworkEngine getNetworkEngine() {
        return this.networkEngine;
    }
}
