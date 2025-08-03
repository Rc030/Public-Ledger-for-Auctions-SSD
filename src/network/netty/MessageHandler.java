package network.netty;

import auction.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import consensus.PoRConsensus;
import consensus.PoWConsensus;
import ledger.Block;
import ledger.Blockchain;
import ledger.Transaction;
import network.HelloPayload;
import network.NetworkEngine;
import network.kad.KadStore;
import network.kad.KademliaID;
import network.kad.Node;
import network.kad.TrustRegistry;
import util.CryptoUtil;
import java.util.Timer;
import java.util.TimerTask;

import java.lang.reflect.Type;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MessageHandler {
    private final KadStore kadStore;
    private final String localNodeId;
    private final String localIp;
    private final int localPort;
    private final Set<String> helloSentRecently = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> recentlyHandledFindValueKeys = ConcurrentHashMap.newKeySet();
    private final Set<String> handledNotifies = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<String> peerListSentRecently = Collections.synchronizedSet(new HashSet<>());

    private NetworkEngine networkEngine;
    private AuctionManager auctionManager;
    private Blockchain blockchain;

    public MessageHandler(KadStore kadStore, String localNodeId, String localIp, int localPort) {
        this.kadStore = kadStore;
        this.localNodeId = localNodeId;
        this.localIp = localIp;
        this.localPort = localPort;
    }

    public void setAuctionManager(AuctionManager auctionManager) {
        this.auctionManager = auctionManager;
    }

    public void setNetworkEngine(NetworkEngine networkEngine) {
        this.networkEngine = networkEngine;
    }

    public void setBlockchain(Blockchain blockchain) {
        this.blockchain = blockchain;
    }

    public void handleMessage(Message message, String senderIp, int senderPort) {
        Gson gson = new Gson();
        String type = message.getType();
        if(!type.equals("NOTIFY")) {
            System.out.println("[NETWORK] Received message type: " + type);
        }

        switch (type) {
            case "SUBSCRIBE":
                PubSubMessage subMsg = gson.fromJson(message.getPayload(), PubSubMessage.class);
                Node subscriber = null;
                subscriber = networkEngine.getPeerManager().getNodeById(message.getSenderId());

                if (subscriber == null) {
                    subscriber = networkEngine.getRoutingTable().findNodeById(message.getSenderId());
                }

                if (subscriber != null) {
                    kadStore.subscribe(subMsg.topic, subscriber);
                    System.out.println("[PUBSUB] Peer subscribed to the topic " + subMsg.topic);
                } else {
                    System.out.println("[PUBSUB] Node not found for registration. ID: " + message.getSenderId());
                }
                break;

            case "PUBLISH":
                PubSubMessage pubMsg = gson.fromJson(message.getPayload(), PubSubMessage.class);
                Set<Node> subscribers = kadStore.getSubscribers(pubMsg.topic);

                for (Node sub : subscribers) {
                    Message notify = new Message("NOTIFY", localNodeId, message.getPayload());
                    networkEngine.sendMessage(sub.getIpAddress().getHostAddress(), sub.getPort(), notify);
                }
                System.out.println("[PUBSUB] Event published to topic: " + pubMsg.topic);
                break;

            case "NOTIFY":
                PubSubMessage notifyMsg = gson.fromJson(message.getPayload(), PubSubMessage.class);
                String eventKey = notifyMsg.topic + ":" + notifyMsg.content + ":" + notifyMsg.timestamp;

                if (handledNotifies.contains(eventKey)) {
                    break;
                }
                handledNotifies.add(eventKey);
                System.out.printf("[PUBSUB] Notification received (topic: %s): %s\n", notifyMsg.topic, notifyMsg.content);
                break;

            case "UNSUBSCRIBE":
                PubSubMessage unsubMsg = gson.fromJson(message.getPayload(), PubSubMessage.class);
                Node unsubscriber = null;
                unsubscriber = networkEngine.getPeerManager().getNodeById(message.getSenderId());

                if (unsubscriber == null) {
                    unsubscriber = networkEngine.getRoutingTable().findNodeById(message.getSenderId());
                }

                if (unsubscriber != null) {
                    kadStore.unsubscribe(unsubMsg.topic, unsubscriber);
                    System.out.println("[PUBSUB] Peer removed from topic " + unsubMsg.topic);
                } else {
                    System.out.println("[PUBSUB] Node not found to unsubscribe. ID: " + message.getSenderId());
                }
                break;


            case "CREATE_AUCTION":
                AuctionService.CreateAuctionPayload payload = gson.fromJson(message.getPayload(), AuctionService.CreateAuctionPayload.class);
                String auctionId = payload.auctionId != null ? payload.auctionId : "auction-" + System.currentTimeMillis();
                boolean exists = auctionManager.getAllAuctions().stream()
                        .anyMatch(a -> a.getAuctionId().equals(auctionId));

                if (!exists) {
                    Auction auction = new Auction(auctionId, payload.itemName, payload.minBid, payload.sellerId);
                    auctionManager.createAuction(auction);
                    System.out.printf("[NETWORK] Received CREATE_AUCTION from %s. Registered auction: name=\"%s\", id=%s%n",
                            message.getSenderId(), payload.itemName, auctionId);
                }
                break;

            case "BID":
                Bid bid = gson.fromJson(message.getPayload(), Bid.class);

                if (message.getSenderId().equals(networkEngine.getLocalNodeId())) {
                    System.out.println("[NETWORK] Ignoring bid sent by the node itself.");
                    break;
                }
                boolean accepted = auctionManager.placeBid(bid);

                if (accepted) {
                    TrustRegistry.trustManager.recordSuccess(message.getSenderId());
                    System.out.println("[NETWORK] Bid accepted for auction " + bid.getAuctionId());
                } else {
                    TrustRegistry.trustManager.recordFailure(message.getSenderId());
                    System.out.println("[NETWORK] Bid rejected for auction " + bid.getAuctionId());
                }
                break;

            case "CLOSE_AUCTION":
                String closedAuctionId = gson.fromJson(message.getPayload(), String.class);
                Auction auctionToClose = auctionManager.getAllAuctions().stream()
                        .filter(a -> a.getAuctionId().equals(closedAuctionId))
                        .findFirst()
                        .orElse(null);

                if (auctionToClose != null) {
                    auctionToClose.setFinished(true);
                    System.out.println("[NETWORK] Auction closed via network: " + closedAuctionId);
                }
                break;

            case "BLOCK":
                Block block = gson.fromJson(message.getPayload(), Block.class);
                System.out.println("[BLOCK] Block received: " + block.getHash());

                if (blockchain.containsBlock(block.getHash())) {
                    System.out.println("[BLOCK] Block already exists locally. Ignoring.");
                    break;
                }

                try {
                    String canonicalData = block.getCanonicalData();
                    boolean validSignature = CryptoUtil.verifySignature(
                            canonicalData.getBytes(),
                            block.getSignature(),
                            CryptoUtil.base64ToPublicKey(block.getPublicKey())
                    );

                    if (!validSignature) {
                        System.err.println("[BLOCK] Error validating block. Rejected ");
                        return;
                    }

                    boolean valid = networkEngine.getConsensusEngine().validateBlock(block, blockchain);
                    if (valid) {
                        boolean added = blockchain.addBlock(block, networkEngine.getConsensusEngine(), networkEngine);

                        if (added) {
                            System.out.println("[BLOCK] Block validated and added successfully.");

                            for (Transaction tx : block.getTransactions()) {
                                String txId = tx.getSenderId() + tx.getPayload() + tx.getTimestamp();

                                if (!networkEngine.getReputationEngine().isTransactionProcessed(txId)) {
                                    double bonus = (networkEngine.getConsensusEngine() instanceof PoRConsensus) ? 0.10 :
                                            (networkEngine.getConsensusEngine() instanceof PoWConsensus) ? 0.05 : 0.03;
                                    networkEngine.getReputationEngine().registerSuccess(tx.getSenderId(), bonus);
                                    networkEngine.getReputationEngine().markTransactionProcessed(txId);
                                    System.out.printf("[REPUTATION] Reputação atualizada para %s (%.2f) (%s block recebido).\n",
                                            tx.getSenderId(), bonus, networkEngine.getConsensusEngine().getName());
                                } else {
                                    System.out.println("[REPUTATION] Reputation already updated for transaction: " + txId);
                                }
                            }
                        } else {
                            System.out.println("[BLOCK] Valid block, but rejected by blockchain.");
                        }

                    } else {
                        System.out.println("[BLOCK] Invalid block (" + networkEngine.getConsensusEngine().getName() + ").");

                        if (!block.getTransactions().isEmpty()) {
                            String senderId = block.getTransactions().get(0).getSenderId();

                            if (networkEngine != null && senderId != null) {
                                double penalty = (networkEngine.getConsensusEngine() instanceof PoRConsensus) ? 0.10 :
                                        (networkEngine.getConsensusEngine() instanceof PoWConsensus) ? 0.02 : 0.05;
                                networkEngine.getReputationEngine().registerFailure(senderId, penalty);
                                System.out.printf("[REPUTATION] Penalty of %.2f applied to %s (%s invalid block).\n",
                                        penalty, senderId, networkEngine.getConsensusEngine().getName());
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[BLOCK] Error validating block: " + e.getMessage());
                    e.printStackTrace();
                }
                break;

            case "HELLO":
                handleHello(message);
                break;

            case "PEER_LIST":
                handlePeerList(message);
                break;

            case "TRANSACTION":
                try {
                    Gson gsonTx = new Gson();
                    Transaction receivedTx = gsonTx.fromJson(message.getPayload(), Transaction.class);
                    String txId = receivedTx.getSenderId() + receivedTx.getPayload() + receivedTx.getTimestamp();

                    if (blockchain.containsTransaction(receivedTx) || networkEngine.getReputationEngine().isTransactionProcessed(txId)) {
                        System.out.println("[TRANSACTION] Transaction already exists in blockchain or reputation already updated. Ignoring.");
                        break;
                    }
                    String txData = receivedTx.getSenderId() + receivedTx.getPayload() + receivedTx.getTimestamp();
                    boolean validSignature = CryptoUtil.verifySignature(
                            txData.getBytes(),
                            receivedTx.getSignature(),
                            CryptoUtil.base64ToPublicKey(receivedTx.getPublicKey())
                    );

                    if (!validSignature) {
                        System.err.println("[TRANSACTION] Invalid transaction signature.");
                        if (networkEngine != null && receivedTx.getSenderId() != null) {
                            networkEngine.getReputationEngine().registerFailure(receivedTx.getSenderId(), 0.15);
                            System.out.printf("[REPUTATION] Severe penalty (0.15) applied to %s for invalid signature.\n", receivedTx.getSenderId());
                        }
                        break;
                    }

                    List<Transaction> txList = new ArrayList<>();
                    txList.add(receivedTx);
                    Block newBlock = networkEngine.getConsensusEngine().generateNextBlock(txList, blockchain, networkEngine.getKeyPair());

                    if (networkEngine.getConsensusEngine().validateBlock(newBlock, blockchain)) {
                        if (blockchain.addBlock(newBlock, networkEngine.getConsensusEngine(), networkEngine)) {
                            System.out.println("[BLOCK] Block created and added with received transaction.");
                            networkEngine.broadcastBlock(newBlock);

                            networkEngine.getReputationEngine().registerSuccess(receivedTx.getSenderId());
                            networkEngine.getReputationEngine().markTransactionProcessed(txId);
                            System.out.printf("[REPUTATION] Reputation increased for %s (%s block created).\n",
                                    receivedTx.getSenderId(), networkEngine.getConsensusEngine().getName());
                        } else {
                            System.err.println("[BLOCK] Block with transaction could not be added.");
                        }
                    } else {
                        System.err.println("[BLOCK] Block generated from transaction was rejected by consensus.");

                        double penalty = (networkEngine.getConsensusEngine() instanceof PoRConsensus) ? 0.10 : 0.03;
                        networkEngine.getReputationEngine().registerFailure(receivedTx.getSenderId(), penalty);
                        System.out.printf("[REPUTATION] Penalty %.2f applied to %s (%s rejected the block).\n",
                                penalty, receivedTx.getSenderId(), networkEngine.getConsensusEngine().getName());
                    }

                } catch (Exception e) {
                    System.err.println("[TRANSACTION] Error processing TRANSACTION: " + e.getMessage());
                    e.printStackTrace();
                }
                break;

            case "BLOCKCHAIN_SYNC":
                Type blockListType = new TypeToken<List<Block>>() {}.getType();
                List<Block> receivedChain = gson.fromJson(message.getPayload(), blockListType);
                blockchain.loadFromReceivedChain(receivedChain);
                System.out.println("[BLOCKCHAIN] Received BLOCKCHAIN_SYNC");
                System.out.println("[BLOCKCHAIN] Blockchain synced from peer. New block count: " + receivedChain.size());
                break;

            case "BLOCKCHAIN_REQUEST":
                String requesterIp = message.getSenderIp();
                int requesterPort = message.getSenderPort();
                networkEngine.sendBlockchainToPeer(requesterIp, requesterPort);
                break;

            case "PING":
                System.out.println("[NETWORK] PING received from " + message.getSenderId());
                Message pong = new Message("PONG", localNodeId, "pong");
                pong.setSenderIp(localIp);
                pong.setSenderPort(localPort);
                networkEngine.sendMessage(senderIp, senderPort, pong);
                break;

            case "PONG":
                System.out.println("[NETWORK] PONG received from " + message.getSenderId());
                break;

            case "STORE":
                try {
                    StorePayload storeData = gson.fromJson(message.getPayload(), StorePayload.class);
                    kadStore.put(storeData.key, storeData.value);
                    System.out.println("[PUBSUB] STORE received and saved: " + storeData.key);
                } catch (Exception e) {
                    System.err.println("[PUBSUB] Error processing STORE: " + e.getMessage());
                }
                break;

            case "FIND_NODE":
                try {
                    byte[] keyHash = MessageDigest.getInstance("SHA-1").digest(message.getPayload().getBytes());
                    KademliaID targetId = new KademliaID(keyHash);

                    System.out.println("[NETWORK] FIND_NODE requested for ID: " + targetId);
                    System.out.println("[NETWORK] My own ID: " + networkEngine.getLocalId());
                    System.out.println("[NETWORK] Number of known peers locally: " + networkEngine.getRoutingTable().getAllNodes().size());

                    List<Node> closest = networkEngine.getRoutingTable().findClosest(targetId, 5);

                    if (closest.isEmpty()) {
                        System.out.println("[NETWORK] No close peers found.");
                    } else {
                        System.out.println("[NETWORK] Peer selection based on S/Kademlia:");
                        for (Node node : closest) {
                            double xorDist = node.getNodeId().getDistance(targetId).doubleValue();
                            double trust = TrustRegistry.trustManager.getTrust(node.getNodeId().toString());
                            double skadScore = 0.65 * xorDist + 0.35 * (1.0 / Math.max(trust, 0.01));
                            System.out.printf("[NETWORK] - %s | XOR: %.4f | Trust: %.2f | S/Kad: %.4f\n",
                                    node.getNodeId(), xorDist, trust, skadScore);
                        }
                    }

                    FindNodeFallback responsePayload = new FindNodeFallback();
                    responsePayload.key = targetId.toString();
                    responsePayload.nodes = closest.toArray(new Node[0]);

                    Message response = new Message("FIND_NODE_RESPONSE", localNodeId, gson.toJson(responsePayload));
                    response.setSenderIp(localIp);
                    response.setSenderPort(localPort);

                    networkEngine.sendMessage(senderIp, senderPort, response);
                } catch (Exception e) {
                    System.err.println("[NETWORK] Error processing FIND_NODE: " + e.getMessage());
                }
                break;

            case "FIND_NODE_RESPONSE":
                System.out.println("[NETWORK] JSON received for analysis: " + message.getPayload());
                try {
                    FindNodeFallback fallback = gson.fromJson(message.getPayload(), FindNodeFallback.class);

                    if (fallback.nodes == null || fallback.nodes.length == 0) {
                        System.out.println("[NETWORK] FIND_NODE_RESPONSE is empty.");
                        break;
                    }

                    if (fallback.key == null || fallback.key.trim().isEmpty()) {
                        System.out.println("[NETWORK] FIND_NODE_RESPONSE with null or empty key.");
                        break;
                    }

                    if (recentlyHandledFindValueKeys.contains(fallback.key)) {
                        System.out.println("[NETWORK] FIND_VALUE ignored (already forwarded for key: " + fallback.key + ")");
                        break;
                    }
                    recentlyHandledFindValueKeys.add(fallback.key);
                    new Timer().schedule(new TimerTask() {
                        @Override
                        public void run() {
                            recentlyHandledFindValueKeys.remove(fallback.key);
                            System.out.println("[NETWORK] FIND_VALUE cache cleared for key (fallback): " + fallback.key);
                        }
                    }, 10_000);

                    System.out.println("[NETWORK] Nodes returned via fallback:");
                    for (Node node : fallback.nodes) {
                        System.out.println("[NETWORK] - " + node);

                        String ip = node.getIpAddress().getHostAddress();
                        int port = node.getPort();

                        if (!ip.equals(localIp) || port != localPort) {
                            Message retry = new Message("FIND_VALUE", localNodeId, fallback.key);
                            retry.setSenderIp(localIp);
                            retry.setSenderPort(localPort);
                            networkEngine.sendMessage(ip, port, retry);
                            System.out.printf("[NETWORK] Forwarding FIND_VALUE to %s:%d\n", ip, port);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[NETWORK] Error interpreting FIND_NODE_RESPONSE: " + e.getMessage());
                }
                break;

            case "FIND_VALUE":
                String lookupKey = message.getPayload();
                System.out.println("[NETWORK] FIND_VALUE requested for key: " + lookupKey);

                if (recentlyHandledFindValueKeys.contains(lookupKey)) {
                    String cachedValue = kadStore.get(lookupKey);

                    if (cachedValue != null) {
                        System.out.println("[NETWORK] Value (cache) resent for " + lookupKey + ": " + cachedValue);
                        Message valueResponse = new Message(
                                "FIND_VALUE_RESPONSE",
                                localNodeId,
                                gson.toJson(new ValuePayload(lookupKey, cachedValue))
                        );
                        valueResponse.setSenderIp(localIp);
                        valueResponse.setSenderPort(localPort);
                        networkEngine.sendMessage(senderIp, senderPort, valueResponse);
                    } else {
                        System.out.println("[NETWORK] FIND_VALUE for key already processed: " + lookupKey);
                    }
                    break;
                }

                recentlyHandledFindValueKeys.add(lookupKey);
                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        recentlyHandledFindValueKeys.remove(lookupKey);
                        System.out.println("[NETWORK] FIND_VALUE cache cleared for key: " + lookupKey);
                    }
                }, 10_000);

                String foundValue = kadStore.get(lookupKey);

                if (foundValue != null) {
                    System.out.println("[NETWORK] Value found locally: " + foundValue);
                    Message valueResponse = new Message(
                            "FIND_VALUE_RESPONSE",
                            localNodeId,
                            gson.toJson(new ValuePayload(lookupKey, foundValue))
                    );
                    valueResponse.setSenderIp(localIp);
                    valueResponse.setSenderPort(localPort);
                    networkEngine.sendMessage(senderIp, senderPort, valueResponse);
                } else {
                    System.out.println("[NETWORK] Value not found. Sending fallback with closest nodes...");
                    KademliaID targetId = new KademliaID(lookupKey);
                    List<Node> fallbackNodes = networkEngine.getRoutingTable().findClosest(targetId, 5);

                    FindNodeFallback responsePayload = new FindNodeFallback();
                    responsePayload.key = lookupKey;
                    responsePayload.nodes = fallbackNodes.toArray(new Node[0]);

                    String payloadJson = gson.toJson(responsePayload);
                    System.out.println("[NETWORK] Fallback payload generated: " + payloadJson);

                    Message fallbackResponse = new Message("FIND_NODE_RESPONSE", localNodeId, payloadJson);
                    fallbackResponse.setSenderIp(localIp);
                    fallbackResponse.setSenderPort(localPort);

                    if (fallbackNodes.isEmpty()) {
                        System.out.println("[NETWORK] No peers found for fallback.");
                    }

                    for (Node peer : fallbackNodes) {
                        String ip = peer.getIpAddress().getHostAddress();
                        int port = peer.getPort();
                        if (ip != null && !ip.equals("127.0.0.1") && port > 1024) {
                            System.out.printf("[NETWORK] Sending fallback to %s:%d\n", ip, port);
                            networkEngine.sendMessage(ip, port, fallbackResponse);
                        } else {
                            System.out.printf("[NETWORK] Peer ignored (invalid fallback): %s:%d\n", ip, port);
                        }
                    }
                }
                break;

            case "FIND_VALUE_RESPONSE":
                System.out.println("[NETWORK] JSON received for analysis: " + message.getPayload());
                try {
                    ValuePayload result = gson.fromJson(message.getPayload(), ValuePayload.class);
                    if (result != null && result.key != null && result.value != null) {
                        System.out.println("[NETWORK] Value found for " + result.key + ": " + result.value);
                    } else {
                        System.out.println("[NETWORK] FIND_VALUE_RESPONSE malformed or incomplete.");
                    }
                } catch (Exception e) {
                    System.err.println("[NETWORK] Error interpreting FIND_VALUE_RESPONSE: " + e.getMessage());
                }
                break;

            default:
                System.out.println("[NETWORK] Unknown message type: " + type);
        }
    }

    public static class StorePayload {
        public String key;
        public String value;

        public StorePayload(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }

    private void handleHello(Message msg) {
        Gson gson = new Gson();
        try {
            HelloPayload payload = gson.fromJson(msg.getPayload(), HelloPayload.class);
            String shortNodeId = payload.nodeId.substring(0, 8) + "..." + payload.nodeId.substring(payload.nodeId.length() - 4);

            if (payload.nodeId.equals(localNodeId)) return;
            if (helloSentRecently.contains(payload.nodeId)) return;

            helloSentRecently.add(payload.nodeId);
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    helloSentRecently.remove(payload.nodeId);
                }
            }, 10000);

            double trustScore = TrustRegistry.trustManager.getTrust(payload.nodeId);
            if (trustScore < 0.2) return;

            Node newNode = new Node(new KademliaID(payload.nodeId), payload.ip, payload.port);

            if (networkEngine.getRoutingTable().hasConflict(newNode)) return;

            long sameIPCount = networkEngine.getPeerManager().getAllKnownPeers().stream()
                    .filter(node -> node.getIpAddress().getHostAddress().equals(payload.ip))
                    .count();
            if (sameIPCount >= 5) return;
            if (networkEngine.getRoutingTable().containsExact(newNode)) return;

            System.out.printf("HELLO received from %s (IP: %s, Port: %d)%n", shortNodeId, payload.ip, payload.port);
            System.out.printf("Initial trust: %.2f%n", trustScore);

            boolean added = networkEngine.getPeerManager().addPeer(payload.nodeId, payload.ip, payload.port);
            if (added) {
                System.out.printf("Peer added to routing table: %s%n", shortNodeId);
            }

            if (!peerListSentRecently.contains(payload.nodeId)) {
                peerListSentRecently.add(payload.nodeId);

                List<Node> peers = new ArrayList<>(networkEngine.getPeerManager().getAllKnownPeers());
                peers.add(networkEngine.getLocalNode());

                String peersJson = gson.toJson(peers.toArray(new Node[0]));
                Message peerListMsg = new Message("PEER_LIST", localNodeId, peersJson);
                peerListMsg.setSenderIp(localIp);
                peerListMsg.setSenderPort(localPort);
                networkEngine.sendMessage(payload.ip, payload.port, peerListMsg);

                System.out.printf("Sent PEER_LIST to %s (%d peers)%n", shortNodeId, peers.size());

                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        peerListSentRecently.remove(payload.nodeId);
                    }
                }, 10000);
            }

            auctionManager.sendAllAuctionsToNode(newNode);

        } catch (Exception e) {
            System.err.println("Error in handleHello: " + e.getMessage());
        }
    }

    private void handlePeerList(Message msg) {
        Gson gson = new Gson();
        try {
            Node[] peers = gson.fromJson(msg.getPayload(), Node[].class);
            System.out.printf("Received PEER_LIST (%d peers)%n", peers.length);

            Set<String> processed = new HashSet<>();

            for (Node peer : peers) {
                String peerId = peer.getNodeId().toString();
                String shortId = getShortId(peerId);
                String ip = peer.getIpAddress().getHostAddress();
                int port = peer.getPort();

                if (peerId.equals(localNodeId)) continue;
                if (processed.contains(peerId)) continue;
                processed.add(peerId);

                if (ip == null || ip.equals("127.0.0.1") || port < 1 || port >= 65536) continue;

                double trust = TrustRegistry.trustManager.getTrust(peerId);
                if (trust < 0.2) continue;

                long ipCount = networkEngine.getPeerManager().getAllKnownPeers().stream()
                        .filter(n -> n.getIpAddress().getHostAddress().equals(ip)).count();
                if (ipCount >= 5) continue;

                boolean added = networkEngine.getPeerManager().addPeer(peerId, ip, port);

                if (added && !helloSentRecently.contains(peerId)) {
                    helloSentRecently.add(peerId);

                    Message hello = new Message("HELLO", localNodeId,
                            gson.toJson(new HelloPayload(localNodeId, localIp, localPort)));
                    hello.setSenderIp(localIp);
                    hello.setSenderPort(localPort);
                    networkEngine.sendMessage(ip, port, hello);

                    System.out.printf("Sent HELLO to %s | IP: %s | Port: %d%n", shortId, ip, port);

                    new Timer().schedule(new TimerTask() {
                        @Override
                        public void run() {
                            helloSentRecently.remove(peerId);
                        }
                    }, 10000);
                }
            }

        } catch (Exception e) {
            System.err.println("Error in handlePeerList: " + e.getMessage());
        }
    }

    private String getShortId(String fullId) {
        return fullId.substring(0, 8) + "..." + fullId.substring(fullId.length() - 4);
    }

    public static class PubSubMessage {
        public String topic;
        public String content;
        public Object timestamp;
    }

    public static class FindNodeFallback {
        public String key;
        public Node[] nodes;
    }

    public static class ValuePayload {
        public String key;
        public String value;

        public ValuePayload(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }
}