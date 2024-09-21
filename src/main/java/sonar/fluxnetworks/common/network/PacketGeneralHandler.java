package sonar.fluxnetworks.common.network;

import com.google.common.collect.Lists;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.Tuple;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import sonar.fluxnetworks.api.gui.EnumFeedbackInfo;
import sonar.fluxnetworks.api.network.*;
import sonar.fluxnetworks.api.tiles.IFluxConnector;
import sonar.fluxnetworks.api.utils.EnergyType;
import sonar.fluxnetworks.api.utils.NBTType;
import sonar.fluxnetworks.common.connection.FluxNetworkCache;
import sonar.fluxnetworks.common.core.FluxUtils;
import sonar.fluxnetworks.common.data.FluxNetworkData;
import sonar.fluxnetworks.common.handler.PacketHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class PacketGeneralHandler {

    public static NBTTagCompound getCreateNetworkPacket(String name, int color, SecurityType security, EnergyType energy, String password) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString(FluxNetworkData.NETWORK_NAME, name);
        tag.setInteger(FluxNetworkData.NETWORK_COLOR, color);
        tag.setInteger(FluxNetworkData.SECURITY_TYPE, security.ordinal());
        tag.setInteger(FluxNetworkData.ENERGY_TYPE, energy.ordinal());
        tag.setString(FluxNetworkData.NETWORK_PASSWORD, password);
        return tag;
    }

    public static IMessage handleCreateNetworkPacket(EntityPlayer player, NBTTagCompound nbtTag) {
        String name = nbtTag.getString(FluxNetworkData.NETWORK_NAME);
        int color = nbtTag.getInteger(FluxNetworkData.NETWORK_COLOR);
        SecurityType security = SecurityType.values()[nbtTag.getInteger(FluxNetworkData.SECURITY_TYPE)];
        EnergyType energy = EnergyType.values()[nbtTag.getInteger(FluxNetworkData.ENERGY_TYPE)];
        String password = nbtTag.getString(FluxNetworkData.NETWORK_PASSWORD);
        if (!FluxUtils.checkPassword(password)) {
            return new PacketFeedback.FeedbackMessage(EnumFeedbackInfo.ILLEGAL_PASSWORD);
        }
        if (FluxNetworkCache.instance.hasSpaceLeft(player)) {
            FluxNetworkCache.instance.createdNetwork(player, name, color, security, energy, password);
            return new PacketFeedback.FeedbackMessage(EnumFeedbackInfo.SUCCESS);
        }
        return new PacketFeedback.FeedbackMessage(EnumFeedbackInfo.NO_SPACE);
    }

    public static NBTTagCompound getNetworkEditPacket(long networkID, String networkName, int color, SecurityType security, EnergyType energy, String password) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong(FluxNetworkData.NETWORK_ID, networkID);
        tag.setString(FluxNetworkData.NETWORK_NAME, networkName);
        tag.setInteger(FluxNetworkData.NETWORK_COLOR, color);
        tag.setInteger(FluxNetworkData.SECURITY_TYPE, security.ordinal());
        tag.setInteger(FluxNetworkData.ENERGY_TYPE, energy.ordinal());
        tag.setString(FluxNetworkData.NETWORK_PASSWORD, password);
        return tag;
    }

    public static IMessage handleNetworkEditPacket(EntityPlayer player, NBTTagCompound tag) {
        long networkID = tag.getLong(FluxNetworkData.NETWORK_ID);
        String newName = tag.getString(FluxNetworkData.NETWORK_NAME);
        int color = tag.getInteger(FluxNetworkData.NETWORK_COLOR);
        SecurityType security = SecurityType.values()[tag.getInteger(FluxNetworkData.SECURITY_TYPE)];
        EnergyType energy = EnergyType.values()[tag.getInteger(FluxNetworkData.ENERGY_TYPE)];
        String password = tag.getString(FluxNetworkData.NETWORK_PASSWORD);
        if (!FluxUtils.checkPassword(password)) {
            return new PacketFeedback.FeedbackMessage(EnumFeedbackInfo.ILLEGAL_PASSWORD);
        }
        IFluxNetwork network = FluxNetworkCache.instance.getNetwork(networkID);
        if (!network.isInvalid()) {
            if (network.getMemberPermission(player).canEdit()) {
                boolean needPacket = false;
                if (!network.getSetting(NetworkSettings.NETWORK_NAME).equals(newName)) {
                    network.setSetting(NetworkSettings.NETWORK_NAME, newName);
                    needPacket = true;
                }
                if (network.getSetting(NetworkSettings.NETWORK_COLOR) != color) {
                    network.setSetting(NetworkSettings.NETWORK_COLOR, color);
                    needPacket = true;
                    @SuppressWarnings("unchecked")
                    List<IFluxConnector> list = network.getConnections(FluxLogicType.ANY);
                    list.forEach(fluxConnector -> fluxConnector.connect(network)); // update color data
                }
                if (needPacket) {
                    @SuppressWarnings("unchecked") HashMap<Long, Tuple<Integer, String>> cache = new HashMap();
                    cache.put(networkID, new Tuple<>(network.getSetting(NetworkSettings.NETWORK_COLOR) | 0xff000000, network.getSetting(NetworkSettings.NETWORK_NAME)));
                    PacketHandler.network.sendToAll(new PacketColorCache.ColorCacheMessage(cache));
                }
                network.setSetting(NetworkSettings.NETWORK_SECURITY, security);
                network.setSetting(NetworkSettings.NETWORK_ENERGY, energy);
                network.setSetting(NetworkSettings.NETWORK_PASSWORD, password);
                PacketHandler.network.sendToAll(
                        new PacketNetworkUpdate.NetworkUpdateMessage(network, NBTType.NETWORK_GENERAL));
                return new PacketFeedback.FeedbackMessage(EnumFeedbackInfo.SUCCESS_2);
            } else {
                return new PacketFeedback.FeedbackMessage(EnumFeedbackInfo.NO_ADMIN);
            }
        }
        return null;
    }

    public static NBTTagCompound getDeleteNetworkPacket(long networkID) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong(FluxNetworkData.NETWORK_ID, networkID);
        return tag;
    }

    public static IMessage handleDeleteNetworkPacket(EntityPlayer player, NBTTagCompound nbtTag) {
        long id = nbtTag.getLong(FluxNetworkData.NETWORK_ID);
        IFluxNetwork toDelete = FluxNetworkCache.instance.getNetwork(id);
        if (!toDelete.isInvalid()) {
            if (toDelete.getMemberPermission(player).canDelete()) {
                FluxNetworkData.get().removeNetwork(toDelete);
                return new PacketFeedback.FeedbackMessage(EnumFeedbackInfo.SUCCESS);
            } else {
                return new PacketFeedback.FeedbackMessage(EnumFeedbackInfo.NO_OWNER);
            }
        }
        return null;
    }

    @Deprecated
    public static NBTTagCompound getAddMemberPacket(long networkID, String playerName) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong(FluxNetworkData.NETWORK_ID, networkID);
        tag.setString("playerName", playerName);
        return tag;
    }

    @Deprecated
    public static IMessage handleAddMemberPacket(EntityPlayer player, NBTTagCompound packetTag) {
        long networkID = packetTag.getLong(FluxNetworkData.NETWORK_ID);
        String playerName = packetTag.getString("playerName");

        IFluxNetwork network = FluxNetworkCache.instance.getNetwork(networkID);
        if (!network.isInvalid()) {
            if (network.getMemberPermission(player).canEdit()) {
                network.addNewMember(playerName);
                return new PacketNetworkUpdate.NetworkUpdateMessage(network, NBTType.NETWORK_PLAYERS);
            } else {
                return new PacketFeedback.FeedbackMessage(EnumFeedbackInfo.NO_ADMIN);
            }
        }
        return null;
    }

    @Deprecated
    public static NBTTagCompound getRemoveMemberPacket(long networkID, UUID playerRemoved) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong(FluxNetworkData.NETWORK_ID, networkID);
        tag.setUniqueId("playerRemoved", playerRemoved);
        return tag;
    }

    @Deprecated
    public static IMessage handleRemoveMemberPacket(EntityPlayer player, NBTTagCompound packetTag) {
        long networkID = packetTag.getLong(FluxNetworkData.NETWORK_ID);
        UUID playerRemoved = packetTag.getUniqueId("playerRemoved");

        IFluxNetwork network = FluxNetworkCache.instance.getNetwork(networkID);
        if (!network.isInvalid()) {
            if (network.getMemberPermission(player).canEdit()) {
                network.removeMember(playerRemoved);
                return new PacketNetworkUpdate.NetworkUpdateMessage(network, NBTType.NETWORK_PLAYERS);
            } else {
                return new PacketFeedback.FeedbackMessage(EnumFeedbackInfo.NO_ADMIN);
            }
        }
        return null;
    }

    public static NBTTagCompound getChangePermissionPacket(long networkID, UUID playerChanged, int type) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong(FluxNetworkData.NETWORK_ID, networkID);
        tag.setUniqueId("playerChanged", playerChanged);
        tag.setInteger("t", type);
        return tag;
    }

    public static IMessage handleChangePermissionPacket(EntityPlayer player, NBTTagCompound packetTag) {
        long networkID = packetTag.getLong(FluxNetworkData.NETWORK_ID);
        UUID playerChanged = packetTag.getUniqueId("playerChanged");
        int type = packetTag.getInteger("t");
        if (playerChanged == null) {
            return null;
        }
            /*if (EntityPlayer.getUUID(player.getGameProfile()).equals(playerChanged)) {
                //don't allow editing of their own permissions...
                return null;
            }*/

        IFluxNetwork network = FluxNetworkCache.instance.getNetwork(networkID);
        if (network.isInvalid()) {
            return null;
        }
        if (!network.getMemberPermission(player).canEdit()) {
            return new PacketFeedback.FeedbackMessage(EnumFeedbackInfo.NO_ADMIN);
        }
        // Create new member
        if (type == 0) {
            EntityPlayer player1 = FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList().getPlayerByUUID(playerChanged);
            //noinspection ConstantConditions
            if (player1 == null) {
                return new PacketFeedback.FeedbackMessage(EnumFeedbackInfo.INVALID_USER);
            }
            NetworkMember newMember = NetworkMember.createNetworkMember(player1, AccessLevel.USER);
            network.getSetting(NetworkSettings.NETWORK_PLAYERS).add(newMember);
            PacketHandler.network.sendTo(new PacketFeedback.FeedbackMessage(EnumFeedbackInfo.SUCCESS), (EntityPlayerMP) player);
            return new PacketNetworkUpdate.NetworkUpdateMessage(network, NBTType.NETWORK_PLAYERS);
        } else {
            Optional<NetworkMember> settings = network.getValidMember(playerChanged);
            if (settings.isPresent()) {
                NetworkMember p = settings.get();
                if (type == 1) {
                    p.setAccessPermission(AccessLevel.ADMIN);
                } else if (type == 2) {
                    p.setAccessPermission(AccessLevel.USER);
                } else if (type == 3) {
                    network.getSetting(NetworkSettings.NETWORK_PLAYERS).remove(p);
                } else if (type == 4) {
                    /*network.getSetting(NetworkSettings.NETWORK_PLAYERS).stream()
                            .filter(f -> f.getAccessPermission().canDelete()).findFirst().ifPresent(s -> s.setAccessPermission(AccessPermission.USER));*/
                    network.getSetting(NetworkSettings.NETWORK_PLAYERS).removeIf(f -> f.getAccessPermission().canDelete());
                    network.setSetting(NetworkSettings.NETWORK_OWNER, playerChanged);
                    p.setAccessPermission(AccessLevel.OWNER);
                }
                PacketHandler.network.sendTo(new PacketFeedback.FeedbackMessage(EnumFeedbackInfo.SUCCESS), (EntityPlayerMP) player);
                return new PacketNetworkUpdate.NetworkUpdateMessage(network, NBTType.NETWORK_PLAYERS);
            } else if (type == 4) {
                EntityPlayer player1 = FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList().getPlayerByUUID(playerChanged);
                //noinspection ConstantConditions
                if (player1 == null) {
                    return new PacketFeedback.FeedbackMessage(EnumFeedbackInfo.INVALID_USER);
                }
                    /*network.getSetting(NetworkSettings.NETWORK_PLAYERS).stream()
                            .filter(f -> f.getAccessPermission().canDelete()).findFirst().ifPresent(s -> s.setAccessPermission(AccessPermission.USER));*/
                network.getSetting(NetworkSettings.NETWORK_PLAYERS).removeIf(f -> f.getAccessPermission().canDelete());
                NetworkMember newMember = NetworkMember.createNetworkMember(player1, AccessLevel.OWNER);
                network.getSetting(NetworkSettings.NETWORK_PLAYERS).add(newMember);
                network.setSetting(NetworkSettings.NETWORK_OWNER, playerChanged);
                PacketHandler.network.sendTo(new PacketFeedback.FeedbackMessage(EnumFeedbackInfo.SUCCESS), (EntityPlayerMP) player);
                return new PacketNetworkUpdate.NetworkUpdateMessage(network, NBTType.NETWORK_PLAYERS);
            }
            return new PacketFeedback.FeedbackMessage(EnumFeedbackInfo.INVALID_USER);
        }
    }

    public static NBTTagCompound getChangeWirelessPacket(long networkID, int wirelessMode) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong(FluxNetworkData.NETWORK_ID, networkID);
        tag.setInteger(FluxNetworkData.WIRELESS_MODE, wirelessMode);
        return tag;
    }

    public static IMessage handleChangeWirelessPacket(EntityPlayer player, NBTTagCompound packetTag) {
        long networkID = packetTag.getLong(FluxNetworkData.NETWORK_ID);
        int wireless = packetTag.getInteger(FluxNetworkData.WIRELESS_MODE);
        IFluxNetwork network = FluxNetworkCache.instance.getNetwork(networkID);
        if (network.isInvalid()) {
            return null;
        }
        if (!network.getMemberPermission(player).canEdit()) {
            return new PacketFeedback.FeedbackMessage(EnumFeedbackInfo.NO_ADMIN);
        }
        network.setSetting(NetworkSettings.NETWORK_WIRELESS, wireless);
        PacketHandler.network.sendTo(
                new PacketNetworkUpdate.NetworkUpdateMessage(network, NBTType.NETWORK_GENERAL),
                (EntityPlayerMP) player
        );
        return new PacketFeedback.FeedbackMessage(EnumFeedbackInfo.SUCCESS);
    }
}
