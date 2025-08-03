package network.netty;

import com.google.gson.Gson;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import util.TLSUtil;

import java.util.concurrent.TimeUnit;

public class P2PClient {
    private static final Gson gson = new Gson();
    private final int localPort;
    private final String localIp;

    public P2PClient(String localIp, int localPort) {
        this.localIp = localIp;
        this.localPort = localPort;
    }

    public void send(String ip, int port, Message message) {
        EventLoopGroup group = new NioEventLoopGroup();

        try {
            SslContext sslContext = TLSUtil.createClientContext();

            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();

                            SslHandler sslHandler = sslContext.newHandler(ch.alloc(), ip, port);
                            p.addFirst(sslHandler);

                            p.addLast(new StringDecoder());
                            p.addLast(new StringEncoder());
                        }
                    });

            ChannelFuture f = bootstrap.connect(ip, port).sync();

            message.setSenderIp(localIp);
            message.setSenderPort(localPort);
            String json = gson.toJson(message);
            f.channel().writeAndFlush(json + "\n");

            f.channel().eventLoop().schedule(() -> {
                f.channel().close();
                group.shutdownGracefully();
            }, 1, TimeUnit.SECONDS);

        } catch (Exception e) {
            System.err.println("[NETWORK] Failed to send message to " + ip + ":" + port + " — " + e.getMessage());
            group.shutdownGracefully();
        }
    }
}
