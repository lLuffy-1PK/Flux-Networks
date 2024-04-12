package sonar.fluxnetworks.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import sonar.fluxnetworks.FluxNetworks;
import sonar.fluxnetworks.api.gui.EnumFeedbackInfo;
import sonar.fluxnetworks.common.handler.PacketHandler;

public class PacketFeedback implements IMessageHandler<PacketFeedback.FeedbackMessage, IMessage> {

    @Override
    public IMessage onMessage(FeedbackMessage message, MessageContext ctx) {
        PacketHandler.handlePacket(() -> {
            FluxNetworks.proxy.setFeedback(message.info, message.info == EnumFeedbackInfo.SUCCESS || message.info == EnumFeedbackInfo.SUCCESS_2 || message.info == EnumFeedbackInfo.PASSWORD_REQUIRE);
        }, ctx.netHandler);
        return null;
    }

    public static class FeedbackMessage implements IMessage {

        public EnumFeedbackInfo info;

        public FeedbackMessage() {
        }

        public FeedbackMessage(EnumFeedbackInfo info) {
            this.info = info;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            info = EnumFeedbackInfo.values()[buf.readInt()];
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(info.ordinal());
        }
    }
}
