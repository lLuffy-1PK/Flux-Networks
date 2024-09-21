package sonar.fluxnetworks.common.data;

import com.google.common.collect.Lists;
import com.mongodb.BasicDBObject;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.FMLCommonHandler;
import org.apache.commons.io.FileUtils;
import sonar.fluxnetworks.FluxNetworks;
import sonar.fluxnetworks.api.network.AccessLevel;
import sonar.fluxnetworks.api.network.IFluxNetwork;
import sonar.fluxnetworks.api.network.ISuperAdmin;
import sonar.fluxnetworks.api.network.NetworkMember;
import sonar.fluxnetworks.api.network.NetworkSettings;
import sonar.fluxnetworks.api.network.SecurityType;
import sonar.fluxnetworks.api.tiles.IFluxConnector;
import sonar.fluxnetworks.api.utils.Capabilities;
import sonar.fluxnetworks.api.utils.EnergyType;
import sonar.fluxnetworks.api.utils.NBTType;
import sonar.fluxnetworks.common.connection.FluxLiteConnector;
import sonar.fluxnetworks.common.connection.FluxNetworkBase;
import sonar.fluxnetworks.common.connection.FluxNetworkCache;
import sonar.fluxnetworks.common.connection.FluxNetworkServer;
import sonar.fluxnetworks.common.data.dto.FluxNetworkDataDTO;
import sonar.fluxnetworks.common.handler.PacketHandler;
import sonar.fluxnetworks.common.network.PacketNetworkUpdate;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static sonar.fluxnetworks.FluxConfig.MONGO_COLLECTION;
import static sonar.fluxnetworks.FluxConfig.MONGO_SERVER_ID;
import static sonar.fluxnetworks.common.data.TagConstants.INDEX_FIELD;
import static sonar.fluxnetworks.common.data.TagConstants.X;
import static sonar.fluxnetworks.common.data.TagConstants.Z;

/**
 * Save network data to local. Only on server side
 */
public class FluxNetworkData extends WorldSavedData {

    private static final String NETWORK_DATA = FluxNetworks.MODID + "data";

    private static FluxNetworkData data;

    public static String NETWORKS = "networks";
    public static String LOADED_CHUNKS = "loadedChunks";
    public static String UNIQUE_ID = "uniqueID";

    public static String NETWORK_ID = "networkID";
    public static String NETWORK_NAME = "networkName";
    public static String NETWORK_COLOR = "networkColor";
    public static String NETWORK_PASSWORD = "networkPassword";
    public static String SECURITY_TYPE = "networkSecurity";
    public static String ENERGY_TYPE = "networkEnergy";
    public static String OWNER_UUID = "ownerUUID";
    public static String WIRELESS_MODE = "wirelessMode";

    public static String PLAYER_LIST = "playerList";
    public static String NETWORK_FOLDERS = "folders";
    public static String UNLOADED_CONNECTIONS = "unloaded";

    public static String OLD_NETWORK_ID = "id";
    public static String OLD_NETWORK_NAME = "name";
    public static String OLD_NETWORK_COLOR = "colour";
    public static String OLD_NETWORK_ACCESS = "access";

    public Map<Long, IFluxNetwork> networks = new HashMap<>();
    public Map<Integer, List<ChunkPos>> loadedChunks = new HashMap<>(); // Forced Chunks

    public long uniqueID = 1;

    public FluxNetworkData(String name) {
        super(name);
    }

    public FluxNetworkData() {
        this(NETWORK_DATA);
    }

    public static void clear() {
        if (data != null) {
            data = null;
            FluxNetworks.logger.info("FluxNetworkData has been unloaded");
        }
    }

    @Override
    public boolean isDirty() {
        return true;
    }

    public static FluxNetworkData get() {
        if (data == null) {
            loadData();
        }
        return data;
    }

    private static void loadData() {
        FluxNetworkDataDTO fluxNetworkDataDTO = MONGO_COLLECTION.find(new BasicDBObject(INDEX_FIELD, MONGO_SERVER_ID)).first();
        FluxNetworkData savedData;
        if (fluxNetworkDataDTO != null  && !fluxNetworkDataDTO.isEmpty()) {
            savedData = fluxNetworkDataDTO.getFluxNetworkData();
        } else {
            World world = DimensionManager.getWorld(0);

            MapStorage mapStorage = world.getMapStorage();
            savedData = (FluxNetworkData) mapStorage.getOrLoadData(FluxNetworkData.class, NETWORK_DATA);

            if (savedData == null) {
                File oldFile = new File(world.getSaveHandler().getWorldDirectory(), "data/sonar.flux.networks.configurations.dat");
                if (oldFile.exists()) {
                    //oldFile.renameTo(new File(oldFile.getParent(), FluxNetworkData.NETWORK_DATA + ".dat"));
                    try {
                        FileUtils.copyFile(oldFile, new File(oldFile.getParent(), FluxNetworkData.NETWORK_DATA + ".dat"));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    FluxNetworks.logger.info("Old FluxNetworkData found");
                    savedData = (FluxNetworkData) mapStorage.getOrLoadData(FluxNetworkData.class, NETWORK_DATA);
                } else {
                    savedData = new FluxNetworkData(NETWORK_DATA);
                    mapStorage.setData(NETWORK_DATA, savedData);
                    FluxNetworks.logger.info("No FluxNetworkData found");
                }
            }
        }

        data = savedData;
        FluxNetworks.logger.info("FluxNetworkData has been successfully loaded");
    }

    public void addNetwork(IFluxNetwork network) {
        networks.putIfAbsent(network.getNetworkID(), network);
        PacketHandler.network.sendToAll(new PacketNetworkUpdate.NetworkUpdateMessage(network, NBTType.NETWORK_GENERAL));
    }

    public void removeNetwork(IFluxNetwork network) {
        PacketHandler.network.sendToAll(new PacketNetworkUpdate.NetworkUpdateMessage(network, NBTType.NETWORK_CLEAR));
        network.onRemoved();
        networks.remove(network.getNetworkID());
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        uniqueID = nbt.getLong(UNIQUE_ID);
        if (nbt.hasKey(NETWORKS)) {
            NBTTagList list = nbt.getTagList(NETWORKS, Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound tag = list.getCompoundTagAt(i);
                FluxNetworkServer network = new FluxNetworkServer();
                if (tag.hasKey(OLD_NETWORK_ID)) {
                    readOldData(network, tag);
                } else {
                    network.readNetworkNBT(tag, NBTType.ALL_SAVE);
                }
                addNetwork(network);
            }
        }
        readChunks(nbt);
        data = this;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        compound.setLong(UNIQUE_ID, uniqueID);

        NBTTagList list = new NBTTagList();
        for (IFluxNetwork network : FluxNetworkCache.instance.getAllNetworks()) {
            NBTTagCompound tag = new NBTTagCompound();
            network.writeNetworkNBT(tag, NBTType.ALL_SAVE);
            list.appendTag(tag);
        }
        compound.setTag(NETWORKS, list);

        NBTTagCompound tag = new NBTTagCompound();
        loadedChunks.forEach((dim, pos) -> writeChunks(dim, pos, tag));
        compound.setTag(LOADED_CHUNKS, tag);
        save(compound);
        return compound;
    }

    private void save(NBTTagCompound nbt) {
        FluxNetworkDataDTO networkDataDTO = FluxNetworkDataDTO.fromNBT(nbt);
        if (networkDataDTO != null) {
            MONGO_COLLECTION.findOneAndReplace(new BasicDBObject(INDEX_FIELD, networkDataDTO.getServerID()), networkDataDTO);
        }
    }

    public static void readPlayers(IFluxNetwork network, @Nonnull NBTTagCompound nbt) {
        if (!nbt.hasKey(PLAYER_LIST)) {
            return;
        }
        List<NetworkMember> a = new ArrayList<>();
        NBTTagList list = nbt.getTagList(PLAYER_LIST, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound c = list.getCompoundTagAt(i);
            a.add(new NetworkMember(c));
        }
        network.setSetting(NetworkSettings.NETWORK_PLAYERS, a);
    }

    public static NBTTagCompound writePlayers(IFluxNetwork network, @Nonnull NBTTagCompound nbt) {
        List<NetworkMember> a = network.getSetting(NetworkSettings.NETWORK_PLAYERS);
        if (!a.isEmpty()) {
            NBTTagList list = new NBTTagList();
            a.forEach(s -> list.appendTag(s.writeNetworkNBT(new NBTTagCompound())));
            nbt.setTag(PLAYER_LIST, list);
        }
        return nbt;
    }

    public static void writeAllPlayers(IFluxNetwork network, @Nonnull NBTTagCompound nbt) {
        List<NetworkMember> a = network.getSetting(NetworkSettings.NETWORK_PLAYERS);
        NBTTagList list = new NBTTagList();
        if (!a.isEmpty()) {
            a.forEach(s -> list.appendTag(s.writeNetworkNBT(new NBTTagCompound())));
        }
        List<EntityPlayerMP> players = FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList().getPlayers();
        if (!players.isEmpty()) {
            players.stream().filter(p -> a.stream().noneMatch(s -> s.getPlayerUUID().equals(p.getUniqueID())))
                    .forEach(s -> list.appendTag(NetworkMember.createNetworkMember(s, getPermission(s)).writeNetworkNBT(new NBTTagCompound())));
        }
        nbt.setTag(PLAYER_LIST, list);
    }

    private static AccessLevel getPermission(EntityPlayer player) {
        ISuperAdmin superAdmin = player.getCapability(Capabilities.SUPER_ADMIN, null);
        return (superAdmin != null && superAdmin.getPermission()) ? AccessLevel.SUPER_ADMIN : AccessLevel.NONE;
    }

    public static void readConnections(IFluxNetwork network, @Nonnull NBTTagCompound nbt) {
        if (!nbt.hasKey(UNLOADED_CONNECTIONS)) {
            return;
        }
        List<IFluxConnector> a = new ArrayList<>();
        NBTTagList list = nbt.getTagList(UNLOADED_CONNECTIONS, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            a.add(new FluxLiteConnector(list.getCompoundTagAt(i)));
        }
        network.getSetting(NetworkSettings.ALL_CONNECTORS).addAll(a);
    }

    public static NBTTagCompound writeConnections(IFluxNetwork network, @Nonnull NBTTagCompound nbt) {
        List<IFluxConnector> a = network.getSetting(NetworkSettings.ALL_CONNECTORS);
        if (!a.isEmpty()) {
            NBTTagList list = new NBTTagList();
            a.forEach(s -> {
                if (!s.isChunkLoaded()) {
                    list.appendTag(s.writeCustomNBT(new NBTTagCompound(), NBTType.DEFAULT));
                }
            });
            nbt.setTag(UNLOADED_CONNECTIONS, list);
        }
        return nbt;
    }

    public static void readAllConnections(IFluxNetwork network, @Nonnull NBTTagCompound nbt) {
        if (!nbt.hasKey(UNLOADED_CONNECTIONS)) {
            return;
        }
        List<IFluxConnector> a = new ArrayList<>();
        NBTTagList list = nbt.getTagList(UNLOADED_CONNECTIONS, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            a.add(new FluxLiteConnector(list.getCompoundTagAt(i)));
        }
        network.setSetting(NetworkSettings.ALL_CONNECTORS, a);
    }

    public static NBTTagCompound writeAllConnections(IFluxNetwork network, @Nonnull NBTTagCompound nbt) {
        List<IFluxConnector> a = network.getSetting(NetworkSettings.ALL_CONNECTORS);
        if (!a.isEmpty()) {
            NBTTagList list = new NBTTagList();
            a.forEach(s -> list.appendTag(s.writeCustomNBT(new NBTTagCompound(), NBTType.DEFAULT)));
            nbt.setTag(UNLOADED_CONNECTIONS, list);
        }
        return nbt;
    }

    private void readChunks(NBTTagCompound nbt) {
        if (!nbt.hasKey(LOADED_CHUNKS)) {
            return;
        }
        NBTTagCompound tags = nbt.getCompoundTag(LOADED_CHUNKS);
        for (String key : tags.getKeySet()) {
            NBTTagList list = tags.getTagList(key, Constants.NBT.TAG_COMPOUND);
            List<ChunkPos> pos = loadedChunks.computeIfAbsent(Integer.valueOf(key), l -> new ArrayList<>());
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound tag = list.getCompoundTagAt(i);
                pos.add(new ChunkPos(tag.getInteger(X), tag.getInteger(Z)));
            }
        }
    }

    private NBTTagCompound writeChunks(int dim, List<ChunkPos> pos, NBTTagCompound nbt) {
        if (!pos.isEmpty()) {
            NBTTagList list = new NBTTagList();
            pos.forEach(p -> {
                NBTTagCompound t = new NBTTagCompound();
                t.setInteger(X, p.x);
                t.setInteger(Z, p.z);
                list.appendTag(t);
            });
            nbt.setTag(String.valueOf(dim), list);
        }
        return nbt;
    }

    private static void readOldData(FluxNetworkBase network, NBTTagCompound nbt) {
        network.network_id.setValue(nbt.getLong(FluxNetworkData.OLD_NETWORK_ID));
        network.network_name.setValue(nbt.getString(FluxNetworkData.OLD_NETWORK_NAME));
        NBTTagCompound color = nbt.getCompoundTag(FluxNetworkData.OLD_NETWORK_COLOR);
        network.network_color.setValue(color.getInteger("red") << 16 | color.getInteger("green") << 8 | color.getInteger("blue"));
        network.network_owner.setValue(nbt.getUniqueId(FluxNetworkData.OWNER_UUID));
        int c = nbt.getInteger(FluxNetworkData.OLD_NETWORK_ACCESS);
        network.network_security.setValue(c > 0 ? SecurityType.ENCRYPTED : SecurityType.PUBLIC);
        network.network_password.setValue(String.valueOf((int) (Math.random() * 1000000)));
        network.network_energy.setValue(EnergyType.RF);
        FluxNetworkData.readPlayers(network, nbt);
        FluxNetworkData.readConnections(network, nbt);
    }

}
