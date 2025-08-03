package network.kad;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;

public class KademliaID {
    private String value;

    public static KademliaID fromPublicKey(PublicKey key) {
        try {
            byte[] encoded = key.getEncoded();
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] hash = sha1.digest(encoded);
            return new KademliaID(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 not available", e);
        }
    }

    public KademliaID(String seed) {
        this.value = sha1(seed);
    }

    public KademliaID(byte[] bytes) {
        this.value = bytesToHex(bytes);
    }

    public byte[] getBytes() {
        return hexStringToByteArray(value);
    }

    public BigInteger getDistance(KademliaID other) {
        byte[] a = this.getBytes();
        byte[] b = other.getBytes();
        byte[] xor = new byte[a.length];

        for (int i = 0; i < a.length; i++) {
            xor[i] = (byte) (a[i] ^ b[i]);
        }

        return new BigInteger(1, xor);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private byte[] hexStringToByteArray(String hex) {
        int len = hex.length();
        byte[] result = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            result[i / 2] = (byte) (
                    (Character.digit(hex.charAt(i), 16) << 4)
                            + Character.digit(hex.charAt(i + 1), 16)
            );
        }
        return result;
    }

    private String sha1(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] bytes = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 not supported", e);
        }
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof KademliaID)) return false;
        KademliaID other = (KademliaID) o;
        return this.value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
