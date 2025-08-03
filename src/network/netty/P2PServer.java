package network.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import util.CryptoUtil;
import util.TLSUtil;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.concurrent.atomic.AtomicBoolean;

public class P2PServer {
    private final int port;
    private final MessageHandler messageHandler;
    private final KeyPair keyPair;
    private static final AtomicBoolean printedTlsLog = new AtomicBoolean(false);

    public P2PServer(int port, MessageHandler handler, KeyPair keyPair) {
        this.port = port;
        this.messageHandler = handler;
        this.keyPair = keyPair;
    }

    public void start() {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();

                            try {
                                X509Certificate cert = CryptoUtil.generateSelfSignedCertificate(keyPair);
                                SslContext sslContext = TLSUtil.createServerContext(cert, keyPair.getPrivate());

                                if (printedTlsLog.compareAndSet(false, true)) {
                                    System.out.println("[NETWORK] TLS enabled on Netty server.");
                                }

                                SslHandler sslHandler = sslContext.newHandler(ch.alloc());
                                pipeline.addFirst(sslHandler);
                            } catch (Exception e) {
                                System.err.println("[NETWORK] Error configuring SSL on server: " + e.getMessage());
                                e.printStackTrace();
                            }

                            pipeline.addLast(new LineBasedFrameDecoder(8192));
                            pipeline.addLast(new StringDecoder(StandardCharsets.UTF_8));
                            pipeline.addLast(new StringEncoder(StandardCharsets.UTF_8));
                            pipeline.addLast(new ServerHandler(messageHandler));
                        }
                    });

            ChannelFuture f = b.bind(port).sync();
            f.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
