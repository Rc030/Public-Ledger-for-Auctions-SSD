package network;

import auction.ReputationEngine;
import com.google.gson.Gson;
import ledger.Block;
import ledger.Blockchain;
import ledger.Transaction;
import network.kad.KadStore;
import network.kad.KademliaID;
import network.kad.Node;
import network.kad.RoutingTable;
import network.netty.Message;
import network.netty.MessageHandler;
import network.netty.P2PClient;
import network.netty.P2PServer;
import auction.AuctionManager;
import consensus.ConsensusEngine;
import util.CryptoUtil;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;

public class NetworkEngine {

    private final RoutingTable routingTable;
    private Blockchain blockchain;
    private final AuctionManager auctionManager;
    private final MessageHandler messageHandler;
    private final P2PServer server;
    private final P2PClient client;
    private ConsensusEngine consensusEngine;
    private final KademliaID localId;
    private final String localHost;
    private final int localPort;
    private final KadStore kadStore = new KadStore();
    private final PeerManager peerManager;
    private final ReputationEngine reputationEngine = new ReputationEngine();
    private KeyPair keyPair;
    private String publicKeyBase64;

    public NetworkEngine(int port, Blockchain blockchain, ConsensusEngine consensusEngine) {
        this.localPort = port;
        this.blockchain = blockchain;
        this.consensusEngine = consensusEngine;

        String ipTemp;
        try {
            ipTemp = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            System.err.println("[NETWORK] Failed to obtain local IP address.");
            throw new RuntimeException("Error retrieving machine IP", e);
        }
        this.localHost = ipTemp;

        try {
            this.keyPair = CryptoUtil.generateKeyPair();
            this.publicKeyBase64 = CryptoUtil.publicKeyToBase64(keyPair.getPublic());
            this.localId = KademliaID.fromPublicKey(keyPair.getPublic());

            String shortKey = publicKeyBase64.length() > 35
                    ? publicKeyBase64.substring(0, 35) + "..."
                    : publicKeyBase64;

            System.out.println("[NETWORK] RSA key pair generated.");
            System.out.println("[NETWORK] Public key (prefix): " + shortKey);

            X509Certificate cert = CryptoUtil.generateSelfSignedCertificate(this.keyPair);
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            System.out.println("[NETWORK] Certificate valid: " + dateFormat.format(cert.getNotBefore()) + " → " + dateFormat.format(cert.getNotAfter()));
        } catch (Exception e) {
            System.err.println("[NETWORK] Error generating key or certificate: " + e.getMessage());
            throw new RuntimeException(e);
        }

        this.routingTable = new RoutingTable(localId, 160, 20);
        this.peerManager = new PeerManager(localId);
        this.auctionManager = new AuctionManager(this.blockchain, this);
        this.auctionManager.setConsensusType(consensusEngine.getName());

        this.messageHandler = new MessageHandler(kadStore, localId.toString(), localHost, localPort);
        this.messageHandler.setAuctionManager(auctionManager);
        this.messageHandler.setBlockchain(blockchain);
        this.messageHandler.setNetworkEngine(this);

        this.server = new P2PServer(port, messageHandler, keyPair);
        this.client = new P2PClient(localHost, localPort);

        System.out.println("[NETWORK] Node started | ID: " + this.localId + " | Port: " + this.localPort);
    }

    public void startServer() {
        new Thread(() -> server.start()).start();
        System.out.println("[NETWORK] P2P Server started on port " + localPort);
    }

    public void bootstrap(String ip, int port) {
        Gson gson = new Gson();

        HelloPayload helloPayload = new HelloPayload(localId.toString(), localHost, localPort);
        String shortId = helloPayload.nodeId.substring(0, 8) + "..." + helloPayload.nodeId.substring(helloPayload.nodeId.length() - 4);

        String helloJson = gson.toJson(helloPayload);
        Message hello = new Message("HELLO", localId.toString(), helloJson);
        sendMessage(ip, port, hello);
        System.out.printf("[NETWORK] Sent HELLO to %s | IP: %s | Port: %d%n", shortId, ip, port);

        String blockchainJson = gson.toJson(this.blockchain.getChain());
        Message blockchainSync = new Message("BLOCKCHAIN_SYNC", localId.toString(), blockchainJson);
        sendMessage(ip, port, blockchainSync);
        System.out.printf("[NETWORK] Sent BLOCKCHAIN_SYNC to %s | Port: %d (%d blocks)%n", ip, port, this.blockchain.getChain().size());
    }

    public void sendMessage(String ip, int port, Message message) {
        try {
            System.out.printf("[NETWORK] Sending [%s] to %s:%d%n", message.getType(), ip, port);
            client.send(ip, port, message);
        } catch (Exception e) {
            System.err.printf("[NETWORK] Error sending message to %s:%d — %s%n", ip, port, e.getMessage());
        }
    }

    public void broadcastMessage(Message message) {
        List<Node> nodes = new ArrayList<>(peerManager.getAllKnownPeers());
        nodes.add(getLocalNode());
        for (Node node : nodes) {
            String ip = node.getIpAddress().getHostAddress();
            int port = node.getPort();
            sendMessage(ip, port, message);
        }
    }

    public void broadcastBlock(Block block) {
        try {
            String canonicalData = block.getCanonicalData();
            String assinatura = CryptoUtil.signData(canonicalData.getBytes(StandardCharsets.UTF_8), this.keyPair.getPrivate());
            block.setSignature(assinatura);
            block.setPublicKey(this.publicKeyBase64);
            System.out.println("[BLOCK] Block signed successfully.");
        } catch (Exception e) {
            System.err.println("[BLOCK] Error signing block: " + e.getMessage());
        }

        Gson gson = new Gson();
        String blockJson = gson.toJson(block);
        Message message = new Message("BLOCK", getLocalNodeId(), blockJson);
        broadcastMessage(message);
    }

    public void sendBlockchainToPeer(String ip, int port) {
        Gson gson = new Gson();
        String blockchainJson = gson.toJson(this.blockchain.getChain());
        Message blockchainSync = new Message("BLOCKCHAIN_SYNC_RESPONSE", getLocalNodeId(), blockchainJson);
        sendMessage(ip, port, blockchainSync);
        System.out.println("[NETWORK] Blockchain sent for on-demand synchronization.");
    }

    public void sendPing(String ip, int port) {
        Message ping = new Message("PING", localId.toString(), "ping");
        sendMessage(ip, port, ping);
    }

    public void findNode(KademliaID target, String ip, int port) {
        Message msg = new Message("FIND_NODE", localId.toString(), target.toString());
        sendMessage(ip, port, msg);
    }

    public void findValue(String key, String ip, int port) {
        Message msg = new Message("FIND_VALUE", localId.toString(), key);
        sendMessage(ip, port, msg);
    }

    public RoutingTable getRoutingTable() {
        return routingTable;
    }

    public PeerManager getPeerManager() {
        return peerManager;
    }

    public AuctionManager getAuctionManager() {
        return auctionManager;
    }

    public ConsensusEngine getConsensusEngine() {
        return consensusEngine;
    }

    public void setConsensusEngine(ConsensusEngine consensusEngine) {
        this.consensusEngine = consensusEngine;
        this.auctionManager.setConsensusType(consensusEngine.getName());
        System.out.println("[CONSENSUS] Consensus updated dynamically to " + consensusEngine.getName());
    }

    public KademliaID getLocalId() {
        return localId;
    }

    public String getLocalNodeId() {
        return localId.toString();
    }

    public String getLocalHost() {
        return localHost;
    }

    public int getLocalPort() {
        return localPort;
    }

    public Node getLocalNode() {
        try {
            return new Node(localId, localHost, localPort);
        } catch (UnknownHostException e) {
            throw new RuntimeException("Error creating local node", e);
        }
    }

    public void broadcastTransaction(Transaction tx) {
        Gson gson = new Gson();
        String json = gson.toJson(tx);
        Message message = new Message("TRANSACTION", getLocalNodeId(), json);
        broadcastMessage(message);
    }

    public ReputationEngine getReputationEngine() {
        return reputationEngine;
    }

    public KeyPair getKeyPair() {
        return keyPair;
    }

    public String getPublicKeyBase64() {
        return publicKeyBase64;
    }

    public KadStore getKadStore() {
        return kadStore;
    }
}
