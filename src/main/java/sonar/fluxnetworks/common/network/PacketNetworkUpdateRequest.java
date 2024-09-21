package sonar.fluxnetworks.common.network;

import com.google.common.collect.Lists;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import sonar.fluxnetworks.api.network.IFluxNetwork;
import sonar.fluxnetworks.api.utils.NBTType;
import sonar.fluxnetworks.common.connection.FluxNetworkCache;

import java.util.List;

public class PacketNetworkUpdateRequest implements IMessageHandler<PacketNetworkUpdateRequest.UpdateRequestMessage, IMessage> {

    @Override
    public IMessage onMessage(UpdateRequestMessage message, MessageContext ctx) {
        List<IFluxNetwork> networks = Lists.newArrayList();

        for (Long i : message.networks) {
            IFluxNetwork network = FluxNetworkCache.instance.getNetwork(i);
            if (!network.isInvalid()) {
                networks.add(network);
            }
        }
        if (!networks.isEmpty()) {
            return new PacketNetworkUpdate.NetworkUpdateMessage(Lists.newArrayList(networks), message.type);
        }
        return null;
    }

    public static class UpdateRequestMessage implements IMessage {

        public List<Long> networks = Lists.newArrayList();
        public NBTType type;

        public UpdateRequestMessage() {
        }

        public UpdateRequestMessage(long networkID, NBTType type) {
            this.networks.add(networkID);
            this.type = type;
        }

        public UpdateRequestMessage(List<IFluxNetwork> networks, NBTType type) {
            networks.forEach(n -> this.networks.add(n.getNetworkID()));
            this.type = type;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            int size = buf.readInt();
            for (int i = 0; i < size; i++) {
                networks.add(buf.readLong());
            }
            type = NBTType.values()[buf.readInt()];
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(networks.size());

            networks.forEach(buf::writeLong);
            buf.writeInt(type.ordinal());
        }
    }
}
