package network.netty;

import com.google.gson.Gson;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class ServerHandler extends SimpleChannelInboundHandler<String> {
    private final MessageHandler messageHandler;
    private final Gson gson = new Gson();

    public ServerHandler(MessageHandler messageHandler) {
        this.messageHandler = messageHandler;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msgJson) {
        try {
            Message message = gson.fromJson(msgJson, Message.class);
            String senderIp = message.getSenderIp();
            int senderPort = message.getSenderPort();
            messageHandler.handleMessage(message, senderIp, senderPort);
        } catch (Exception e) {
            System.err.println("[NETWORK] Failed to parse incoming message: " + e.getMessage());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.err.println("[NETWORK] Channel error: " + cause.getMessage());
        ctx.close();
    }
}
