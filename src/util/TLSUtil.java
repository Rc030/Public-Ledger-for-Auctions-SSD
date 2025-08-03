package util;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

public class TLSUtil {

    public static SslContext createServerContext(X509Certificate certificate, PrivateKey privateKey) throws Exception {
        return SslContextBuilder
                .forServer(privateKey, certificate)
                .build();
    }

    public static SslContext createClientContext() throws Exception {
        // Em ambiente real: usar TrustManager com CA. Aqui usamos um sem validação (autoassinados).
        return SslContextBuilder
                .forClient()
                .trustManager(io.netty.handler.ssl.util.InsecureTrustManagerFactory.INSTANCE)
                .build();
    }
}
