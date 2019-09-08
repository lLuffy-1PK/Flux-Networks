package fluxnetworks.common.network;

import fluxnetworks.FluxConfig;
import fluxnetworks.api.FeedbackInfo;
import fluxnetworks.api.network.FluxType;
import fluxnetworks.api.network.IFluxNetwork;
import fluxnetworks.common.connection.FluxNetworkCache;
import fluxnetworks.common.data.FluxChunkManager;
import fluxnetworks.common.data.FluxNetworkData;
import fluxnetworks.common.connection.FluxNetworkServer;
import fluxnetworks.common.connection.NetworkSettings;
import fluxnetworks.common.tileentity.TileFluxCore;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.ChunkPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

public class PacketTileHandler {

    public static NBTTagCompound getSetNetworkPacket(int id, String password) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(FluxNetworkData.NETWORK_ID, id);
        tag.setString(FluxNetworkData.NETWORK_PASSWORD, password);
        return tag;
    }

    public static IMessage handleSetNetworkPacket(TileFluxCore tile, EntityPlayer player, NBTTagCompound tag) {
        int id = tag.getInteger(FluxNetworkData.NETWORK_ID);
        String pass = tag.getString(FluxNetworkData.NETWORK_PASSWORD);
        if(tile.getNetworkID() == id) {
            return null;
        }
        IFluxNetwork network = FluxNetworkCache.instance.getNetwork(id);
        if(!network.isInvalid()) {
            if(tile.getConnectionType().isController() && network.getConnections(FluxType.controller).size() > 0) {
                return new PacketFeedback.FeedbackMessage(FeedbackInfo.HAS_CONTROLLER);
            }
            if(!network.getMemberPermission(player).canAccess()) {
                if(pass.isEmpty()) {
                    return new PacketFeedback.FeedbackMessage(FeedbackInfo.PASSWORD_REQUIRE);
                }
                if (!pass.equals(network.getSetting(NetworkSettings.NETWORK_PASSWORD))) {
                    return new PacketFeedback.FeedbackMessage(FeedbackInfo.REJECT);
                }
            }
            if(tile.getNetwork() != null && !tile.getNetwork().isInvalid()) {
                tile.getNetwork().queueConnectionRemoval(tile, false);
            }
            tile.playerUUID = EntityPlayer.getUUID(player.getGameProfile());
            network.queueConnectionAddition(tile);
            return new PacketFeedback.FeedbackMessage(FeedbackInfo.SUCCESS);
        }
        return null;
    }

    public static NBTTagCompound getChunkLoadPacket(boolean chunkLoading) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("c", chunkLoading);
        return tag;
    }

    public static IMessage handleChunkLoadPacket(TileFluxCore tile, EntityPlayer player, NBTTagCompound tag) {
        boolean load = tag.getBoolean("c");
        if(FluxConfig.enableChunkLoading) {
            if (load) {
                boolean p = FluxChunkManager.forceChunk(tile.getWorld(), new ChunkPos(tile.getPos()));
                tile.chunkLoading = p;
                if(!p) {
                    return new PacketFeedback.FeedbackMessage(FeedbackInfo.HAS_LOADER);
                }
                return null;
            } else {
                FluxChunkManager.releaseChunk(tile.getWorld(), new ChunkPos(tile.getPos()));
                tile.chunkLoading = false;
                return null;
            }
        } else {
            tile.chunkLoading = false;
        }
        return new PacketFeedback.FeedbackMessage(FeedbackInfo.BANNED_LOADING);
    }
}
