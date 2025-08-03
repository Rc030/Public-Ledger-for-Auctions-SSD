package ledger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class BlockchainStorage {
    private static final String FILE_PATH = "blockchain.json";
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void saveBlockchain(List<Block> blockchain) {
        try (FileWriter writer = new FileWriter(FILE_PATH)) {
            gson.toJson(blockchain, writer);
            System.out.println("[BLOCKCHAIN] Blockchain saved to " + FILE_PATH);
        } catch (IOException e) {
            System.err.println("[BLOCKCHAIN] Failed to save blockchain: " + e.getMessage());
        }
    }
}
