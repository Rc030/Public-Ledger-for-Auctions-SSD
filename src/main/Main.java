package main;

import auction.Auction;
import auction.AuctionManager;
import auction.Bid;
import com.google.gson.Gson;
import consensus.ConsensusEngine;
import consensus.PoWConsensus;
import ledger.Block;
import ledger.Blockchain;
import ledger.Transaction;
import network.NetworkEngine;
import network.kad.KademliaID;
import network.kad.Node;
import network.netty.Message;
import network.netty.MessageHandler;

import java.util.*;

import static util.AttackSimulator.simulateEclipseAttack;
import static util.AttackSimulator.simulateSybilAttack;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.print("Port to listen on: ");
        int port = Integer.parseInt(scanner.nextLine().trim());

        ConsensusEngine consensusEngine = new PoWConsensus();
        System.out.println("Started with PoW consensus. Reputation will determine automatic switching.");

        Blockchain blockchain = new Blockchain();
        NetworkEngine network = new NetworkEngine(port, blockchain, consensusEngine);
        AuctionManager auctionManager = network.getAuctionManager();
        String id = network.getLocalNodeId();
        network.startServer();

        System.out.print("Do you want to connect to another node? (y/n) ");
        String connect = scanner.nextLine().trim().toLowerCase();
        if (connect.equals("y")) {
            System.out.print("Remote node IP: ");
            String remoteIp = scanner.nextLine().trim();
            System.out.print("Remote node port: ");
            int remotePort = Integer.parseInt(scanner.nextLine().trim());
            network.bootstrap(remoteIp, remotePort);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        while (true) {
            System.out.println("\n====== MAIN MENU ======");
            System.out.println("1. Auctions");
            System.out.println("2. Blockchain");
            System.out.println("3. P2P Network");
            System.out.println("4. Attacks and Simulations");
            System.out.println("5. Pub/Sub");
            System.out.println("0. Exit");
            System.out.print("Select an option: ");
            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1":
                    auctionMenu(scanner, auctionManager, id);
                    break;
                case "2":
                    blockchainMenu(scanner, blockchain, network, id);
                    break;
                case "3":
                    networkMenu(scanner, network, id);
                    break;
                case "4":
                    attackMenu(scanner, network);
                    break;
                case "5":
                    pubSubMenu(scanner, network, id);
                    break;
                case "0":
                    System.out.println("Exiting...");
                    return;
                default:
                    System.out.println("Invalid option.");
            }
        }
    }

    public static void auctionMenu(Scanner scanner, AuctionManager auctionManager, String localNodeId) {
        while (true) {
            System.out.println("\n====== AUCTIONS MENU ======");
            System.out.println("1. Create auction");
            System.out.println("2. Place bid");
            System.out.println("3. Close auction");
            System.out.println("4. View open auctions");
            System.out.println("5. View bids and winner");
            System.out.println("0. Back to main menu");
            System.out.print("Select an option: ");
            String option = scanner.nextLine().trim();

            switch (option) {
                case "1":
                    System.out.print("Auction name: ");
                    String name = scanner.nextLine().trim();
                    System.out.print("Initial minimum value: ");
                    double minBid = Double.parseDouble(scanner.nextLine().trim());
                    String auctionId = UUID.randomUUID().toString();
                    auctionManager.createAuction(auctionId, name, minBid, localNodeId);
                    System.out.printf("Auction created with ID: %s%n", auctionId);
                    break;

                case "2":
                    System.out.print("Auction ID: ");
                    String auctionIdBid = scanner.nextLine().trim();
                    System.out.print("Bid value: ");
                    double value = Double.parseDouble(scanner.nextLine().trim());
                    long timestamp = System.currentTimeMillis();

                    double trustScore = auctionManager.getNetworkEngine()
                            .getReputationEngine()
                            .getTrustScore(auctionManager.getNetworkEngine().getLocalNodeId());

                    Bid bid = new Bid(auctionIdBid, value, localNodeId, timestamp, trustScore);
                    auctionManager.broadcastBid(bid);
                    System.out.println("Bid sent to the network.");
                    break;

                case "3":
                    System.out.print("Auction ID: ");
                    String auctionIdToClose = scanner.nextLine().trim();
                    boolean closed = auctionManager.closeAuction(auctionIdToClose);
                    System.out.println(closed ? "Auction closed." : "Failed to close auction.");
                    break;

                case "4":
                    List<Auction> openAuctions = auctionManager.getOpenAuctions();
                    if (openAuctions.isEmpty()) {
                        System.out.println("No open auctions.");
                    } else {
                        for (Auction auction : openAuctions) {
                            System.out.printf("%s - %s (Minimum bid: %.2f)%n",
                                    auction.getAuctionId(), auction.getItemName(), auction.getMinBid());
                        }
                    }
                    break;

                case "5":
                    System.out.print("Auction ID: ");
                    String auctionIdToView = scanner.nextLine().trim();
                    auctionManager.viewBidsAndWinner(auctionIdToView);
                    break;

                case "0":
                    return;

                default:
                    System.out.println("Invalid option.");
            }
        }
    }

    public static void blockchainMenu(Scanner scanner, Blockchain blockchain, NetworkEngine network, String localNodeId) {
        while (true) {
            System.out.println("\n====== BLOCKCHAIN MENU ======");
            System.out.println("1. View local blockchain");
            System.out.println("2. Test PoW");
            System.out.println("3. Test PoW with failure");
            System.out.println("4. Test PoR");
            System.out.println("5. Test PoR with failure");
            System.out.println("0. Back to main menu");
            System.out.print("Select an option: ");
            String option = scanner.nextLine().trim();

            switch (option) {
                case "1":
                    List<Block> blocks = blockchain.getChain();
                    if (blocks.isEmpty()) {
                        System.out.println("Blockchain is empty.");
                    } else {
                        for (Block b : blocks) {
                            System.out.println("Block: " + b.getHash());
                            System.out.println("Previous: " + b.getPreviousHash());
                            System.out.println("Timestamp: " + new Date(b.getTimestamp()));
                            System.out.println("Transactions:");
                            if (b.getTransactions().isEmpty()) {
                                System.out.println("  (none)");
                            } else {
                                for (Transaction tx : b.getTransactions()) {
                                    System.out.println("  Sender: " + tx.getSenderId());
                                    System.out.println("  Payload: " + tx.getPayload());
                                    System.out.println("  Signature: " + tx.getSignature());
                                    System.out.println("  Trust: " + tx.getTrustScore());
                                    System.out.println("  Timestamp: " + new Date(tx.getTimestamp()));
                                    System.out.println("  -------------------");
                                }
                            }
                        }
                    }
                    break;

                case "2":
                    List<Transaction> testTx = new ArrayList<>();
                    try {
                        String payload = "Test Transaction";
                        long ts = System.currentTimeMillis();
                        String message = localNodeId + payload + ts;
                        String signature = util.CryptoUtil.signData(message.getBytes(), network.getKeyPair().getPrivate());

                        Transaction tx = new Transaction(localNodeId, payload, ts, signature);
                        tx.setPublicKey(network.getPublicKeyBase64());
                        tx.setTrustScore(1.0);
                        testTx.add(tx);

                        Block testBlock = network.getConsensusEngine().generateNextBlock(testTx, blockchain, network.getKeyPair());
                        boolean valid = network.getConsensusEngine().validateBlock(testBlock, blockchain);
                        System.out.println(valid ? "Block is valid." : "Validation failed.");
                        if (valid) {
                            boolean added = blockchain.addBlock(testBlock, network.getConsensusEngine(), network);
                            System.out.println(added ? "Block added to blockchain." : "Block rejected.");
                            if (added) {
                                network.broadcastBlock(testBlock);
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Error creating or validating block: " + e.getMessage());
                    }
                    break;

                case "3":
                    List<Transaction> badTxList = new ArrayList<>();
                    try {
                        String payload = "Malformed Transaction";
                        long now = System.currentTimeMillis();
                        String msg = localNodeId + payload + now;
                        String badSig = util.CryptoUtil.signData(msg.getBytes(), network.getKeyPair().getPrivate());

                        Transaction badTx = new Transaction(localNodeId, payload, now, badSig);
                        badTx.setPublicKey(network.getPublicKeyBase64());
                        badTx.setTrustScore(1.0);
                        badTxList.add(badTx);

                        Block badBlock = new Block(blockchain.getLatestBlock().getHash(), badTxList);
                        boolean valid = network.getConsensusEngine().validateBlock(badBlock, blockchain);
                        System.out.println(valid ? "Invalid block passed validation." : "Invalid block rejected as expected.");

                        boolean added = blockchain.addBlock(badBlock, network.getConsensusEngine(), network);
                        System.out.println(added ? "Error: invalid block was added." : "Invalid block was rejected as expected.");
                        network.broadcastBlock(badBlock);
                    } catch (Exception e) {
                        System.err.println("Error during PoW failure test: " + e.getMessage());
                    }
                    break;

                case "4":
                    List<Transaction> txList = new ArrayList<>();
                    long now = System.currentTimeMillis();
                    String payload = "Test PoR Transaction";

                    try {
                        String message = localNodeId + payload + now;
                        String signature = util.CryptoUtil.signData(message.getBytes(), network.getKeyPair().getPrivate());

                        Transaction tx = new Transaction(localNodeId, payload, now, signature);
                        tx.setPublicKey(network.getPublicKeyBase64());
                        tx.setTrustScore(1.0);
                        txList.add(tx);

                        Block blockPoR = network.getConsensusEngine().generateNextBlock(txList, blockchain, network.getKeyPair());
                        boolean valid = network.getConsensusEngine().validateBlock(blockPoR, blockchain);
                        System.out.println(valid ? "Block is valid." : "Validation failed.");

                        boolean added = blockchain.addBlock(blockPoR, network.getConsensusEngine(), network);
                        System.out.println(added ? "Block added to blockchain." : "Block rejected.");
                        if (added) {
                            network.broadcastBlock(blockPoR);
                        }
                    } catch (Exception e) {
                        System.err.println("Error during PoR test: " + e.getMessage());
                    }
                    break;

                case "5":
                    List<Transaction> txListLow = new ArrayList<>();
                    long nowLow = System.currentTimeMillis();
                    String payloadLow = "Low trust PoR";

                    try {
                        String msg = localNodeId + payloadLow + nowLow;
                        String signature = util.CryptoUtil.signData(msg.getBytes(), network.getKeyPair().getPrivate());

                        Transaction tx = new Transaction(localNodeId, payloadLow, nowLow, signature);
                        tx.setPublicKey(network.getPublicKeyBase64());
                        tx.setTrustScore(0.3);
                        txListLow.add(tx);

                        Block invalidBlock = network.getConsensusEngine().generateNextBlock(txListLow, blockchain, network.getKeyPair());
                        boolean valid = network.getConsensusEngine().validateBlock(invalidBlock, blockchain);
                        System.out.println(valid ? "Unexpected validation pass." : "Validation correctly failed.");

                        boolean added = blockchain.addBlock(invalidBlock, network.getConsensusEngine(), network);
                        System.out.println(added ? "Error: invalid block was added." : "Invalid block was rejected.");
                        network.broadcastBlock(invalidBlock);
                    } catch (Exception e) {
                        System.err.println("Error during PoR failure test: " + e.getMessage());
                    }
                    break;

                case "0":
                    return;

                default:
                    System.out.println("Invalid option.");
            }
        }
    }

    public static void networkMenu(Scanner scanner, NetworkEngine network, String localNodeId) {
        while (true) {
            System.out.println("\n====== P2P NETWORK MENU ======");
            System.out.println("1. View known peers");
            System.out.println("2. Send PING");
            System.out.println("3. Send STORE");
            System.out.println("4. Send FIND_NODE");
            System.out.println("5. Send FIND_VALUE");
            System.out.println("0. Back to main menu");
            System.out.print("Select an option: ");
            String option = scanner.nextLine().trim();

            switch (option) {
                case "1":
                    List<Node> nodes = network.getPeerManager().getAllKnownPeers();
                    Set<String> uniqueIds = new HashSet<>();
                    System.out.println("Known peers:");
                    for (Node node : nodes) {
                        if (uniqueIds.add(node.getNodeId().toString())) {
                            System.out.println(" - " + node.getNodeId());
                        }
                    }
                    break;

                case "2":
                    System.out.print("Peer IP: ");
                    String ipPing = scanner.nextLine().trim();
                    System.out.print("Peer port: ");
                    int portPing = Integer.parseInt(scanner.nextLine().trim());
                    network.sendPing(ipPing, portPing);
                    break;

                case "3":
                    System.out.print("Key to store: ");
                    String key = scanner.nextLine().trim();
                    System.out.print("Value to store: ");
                    String value = scanner.nextLine().trim();
                    System.out.print("Peer IP: ");
                    String ipStore = scanner.nextLine().trim();
                    System.out.print("Peer port: ");
                    int portStore = Integer.parseInt(scanner.nextLine().trim());

                    Gson gson = new Gson();
                    MessageHandler.StorePayload storePayload = new MessageHandler.StorePayload(key, value);
                    String jsonPayload = gson.toJson(storePayload);
                    Message storeMessage = new Message("STORE", localNodeId, jsonPayload);
                    storeMessage.setSenderIp(network.getLocalHost());
                    storeMessage.setSenderPort(network.getLocalPort());

                    network.sendMessage(ipStore, portStore, storeMessage);
                    break;

                case "4":
                    System.out.print("Node ID to search: ");
                    String targetNodeId = scanner.nextLine().trim();
                    System.out.print("Peer IP: ");
                    String ipFind = scanner.nextLine().trim();
                    System.out.print("Peer port: ");
                    int portFind = Integer.parseInt(scanner.nextLine().trim());
                    network.findNode(new KademliaID(targetNodeId), ipFind, portFind);
                    break;

                case "5":
                    System.out.print("Key of value to search: ");
                    String searchKey = scanner.nextLine().trim();
                    System.out.print("Peer IP: ");
                    String ipValue = scanner.nextLine().trim();
                    System.out.print("Peer port: ");
                    int portValue = Integer.parseInt(scanner.nextLine().trim());
                    network.findValue(searchKey, ipValue, portValue);
                    break;

                case "0":
                    return;

                default:
                    System.out.println("Invalid option.");
            }
        }
    }

    public static void attackMenu(Scanner scanner, NetworkEngine network) {
        while (true) {
            System.out.println("\n====== ATTACKS AND SIMULATIONS MENU ======");
            System.out.println("1. Simulate Sybil attack");
            System.out.println("2. Simulate Eclipse attack");
            System.out.println("0. Back to main menu");
            System.out.print("Select an option: ");
            String option = scanner.nextLine().trim();

            switch (option) {
                case "1":
                    simulateSybilAttack(network);
                    break;

                case "2":
                    simulateEclipseAttack(network);
                    break;

                case "0":
                    return;

                default:
                    System.out.println("Invalid option.");
            }
        }
    }

    public static void pubSubMenu(Scanner scanner, NetworkEngine network, String localNodeId) {
        while (true) {
            System.out.println("\n====== PUB/SUB MENU ======");
            System.out.println("1. Subscribe to topic");
            System.out.println("2. Publish to topic");
            System.out.println("3. Unsubscribe from topic");
            System.out.println("0. Back to main menu");
            System.out.print("Select an option: ");
            String option = scanner.nextLine().trim();

            switch (option) {
                case "1":
                    System.out.print("Enter topic name: ");
                    String topic = scanner.nextLine().trim();

                    Gson gson = new Gson();
                    MessageHandler.PubSubMessage subMsg = new MessageHandler.PubSubMessage();
                    subMsg.topic = topic;
                    subMsg.content = "";

                    Message subscribeMsg = new Message("SUBSCRIBE", localNodeId, gson.toJson(subMsg));
                    network.broadcastMessage(subscribeMsg);
                    System.out.println("Subscription request sent for topic: " + topic);
                    break;

                case "2":
                    System.out.print("Enter topic name: ");
                    String pubTopic = scanner.nextLine().trim();
                    System.out.print("Enter content: ");
                    String content = scanner.nextLine().trim();

                    Gson gsonPub = new Gson();
                    MessageHandler.PubSubMessage pubMsg = new MessageHandler.PubSubMessage();
                    pubMsg.topic = pubTopic;
                    pubMsg.content = content;
                    pubMsg.timestamp = System.currentTimeMillis();

                    Message publishMsg = new Message("PUBLISH", localNodeId, gsonPub.toJson(pubMsg));
                    network.broadcastMessage(publishMsg);
                    System.out.println("Message published to topic: " + pubTopic);
                    break;

                case "3":
                    System.out.print("Enter topic name: ");
                    String unsubTopic = scanner.nextLine().trim();

                    network.getKadStore().unsubscribe(unsubTopic, network.getLocalNode());
                    System.out.println("Unsubscribed locally from topic: " + unsubTopic);

                    Gson gsonUnsub = new Gson();
                    MessageHandler.PubSubMessage unsubPayload = new MessageHandler.PubSubMessage();
                    unsubPayload.topic = unsubTopic;
                    String jsonUnsub = gsonUnsub.toJson(unsubPayload);

                    Message unsubMsg = new Message("UNSUBSCRIBE", localNodeId, jsonUnsub);
                    unsubMsg.setSenderIp(network.getLocalHost());
                    unsubMsg.setSenderPort(network.getLocalPort());

                    for (Node peer : network.getPeerManager().getAllKnownPeers()) {
                        network.sendMessage(peer.getIpAddress().getHostAddress(), peer.getPort(), unsubMsg);
                    }

                    System.out.println("Unsubscribe message sent to peers for topic: " + unsubTopic);
                    break;

                case "0":
                    return;

                default:
                    System.out.println("Invalid option.");
            }
        }
    }
}
