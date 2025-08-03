package util;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.math.BigInteger;
import java.security.*;
import java.security.cert.X509Certificate;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;

public class CryptoUtil {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public static KeyPair generateKeyPair() throws NoSuchAlgorithmException, NoSuchProviderException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA", "BC");
        keyGen.initialize(2048);
        return keyGen.generateKeyPair();
    }

    public static String signData(byte[] data, PrivateKey privateKey) throws Exception {
        Signature signer = Signature.getInstance("SHA256withRSA", "BC");
        signer.initSign(privateKey);
        signer.update(data);
        byte[] signature = signer.sign();
        return Base64.getEncoder().encodeToString(signature);
    }

    public static boolean verifySignature(byte[] data, String signature, PublicKey publicKey) throws Exception {
        Signature verifier = Signature.getInstance("SHA256withRSA", "BC");
        verifier.initVerify(publicKey);
        verifier.update(data);
        return verifier.verify(Base64.getDecoder().decode(signature));
    }

    public static String publicKeyToBase64(PublicKey publicKey) {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    public static PublicKey base64ToPublicKey(String base64) throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance("RSA", "BC");
        return keyFactory.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
    }

    public static X509Certificate generateSelfSignedCertificate(KeyPair keyPair) throws Exception {
        long now = System.currentTimeMillis();
        Date notBefore = new Date(now - 60_000);
        Date notAfter = new Date(now + 1000L * 60 * 60 * 24 * 365); // valid for 1 year

        X500Name subject = new X500Name("CN=AuctionNode");
        BigInteger serial = BigInteger.valueOf(now);

        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                subject,
                serial,
                notBefore,
                notAfter,
                subject,
                keyPair.getPublic()
        );

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider("BC")
                .build(keyPair.getPrivate());

        return new JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(certBuilder.build(signer));
    }
}
