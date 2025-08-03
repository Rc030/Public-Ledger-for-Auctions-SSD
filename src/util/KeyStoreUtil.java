package util;

import java.io.*;
import java.security.*;
import java.security.spec.*;
import java.util.Base64;

public class KeyStoreUtil {

    private static final String KEY_FOLDER = "keystore/";

    public static KeyPair loadOrCreateKeyPair(String userId) {
        try {
            File folder = new File(KEY_FOLDER);
            if (!folder.exists()) folder.mkdirs();

            File pubFile = new File(KEY_FOLDER + userId + ".pub");
            File privFile = new File(KEY_FOLDER + userId + ".key");

            if (pubFile.exists() && privFile.exists()) {
                byte[] pubBytes = Base64.getDecoder().decode(readFile(pubFile));
                byte[] privBytes = Base64.getDecoder().decode(readFile(privFile));

                KeyFactory kf = KeyFactory.getInstance("RSA");
                PublicKey pubKey = kf.generatePublic(new X509EncodedKeySpec(pubBytes));
                PrivateKey privKey = kf.generatePrivate(new PKCS8EncodedKeySpec(privBytes));

                return new KeyPair(pubKey, privKey);
            }

            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            KeyPair keyPair = keyGen.generateKeyPair();

            writeFile(pubFile, Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
            writeFile(privFile, Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()));

            System.out.println("[KEYSTORE] Registered user: " + userId);
            return keyPair;

        } catch (Exception e) {
            throw new RuntimeException("[KEYSTORE] Failed to load or create key pair for " + userId, e);
        }
    }

    private static String readFile(File file) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(file));
        return br.readLine();
    }

    private static void writeFile(File file, String content) throws IOException {
        BufferedWriter bw = new BufferedWriter(new FileWriter(file));
        bw.write(content);
        bw.close();
    }
}
