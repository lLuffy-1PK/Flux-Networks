package fluxnetworks.common.connection;

import fluxnetworks.FluxConfig;
import fluxnetworks.FluxNetworks;
import fluxnetworks.api.AccessPermission;
import fluxnetworks.api.Capabilities;
import fluxnetworks.api.SecurityType;
import fluxnetworks.api.EnergyType;
import fluxnetworks.api.network.FluxType;
import fluxnetworks.api.network.ISuperAdmin;
import fluxnetworks.api.tileentity.IFluxConnector;
import fluxnetworks.api.tileentity.IFluxPlug;
import fluxnetworks.api.tileentity.IFluxPoint;
import fluxnetworks.common.event.FluxConnectionEvent;
import fluxnetworks.common.core.FluxUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

/**
 * Flux Network.
 */
public class FluxNetworkServer extends FluxNetworkBase {

    public HashMap<FluxType, List<IFluxConnector>> connections = new HashMap<>();
    public Queue<IFluxConnector> toAdd = new ConcurrentLinkedQueue<>();
    public Queue<IFluxConnector> toRemove = new ConcurrentLinkedQueue<>();

    public boolean sortConnections = true;

    public List<PriorityGroup<IFluxPlug>> sortedPlugs = new ArrayList<>();
    public List<PriorityGroup<IFluxPoint>> sortedPoints = new ArrayList<>();

    private TransferIterator<IFluxPlug> plugTransferIterator = new TransferIterator<>();
    private TransferIterator<IFluxPoint> pointTransferIterator = new TransferIterator<>();

    public long bufferLimiter = 0;

    public FluxNetworkServer() {
        super();
    }

    public FluxNetworkServer(int id, String name, SecurityType security, int color, UUID owner, EnergyType energy, String password) {
        super(id, name, security, color, owner, energy, password);
    }

    public void addConnections() {
        if(toAdd.isEmpty()) {
            return;
        }
        Iterator<IFluxConnector> iterator = toAdd.iterator();
        while(iterator.hasNext()) {
            IFluxConnector flux = iterator.next();
            FluxType.getValidTypes(flux).forEach(t -> FluxUtils.addWithCheck(getConnections(t), flux));
            MinecraftForge.EVENT_BUS.post(new FluxConnectionEvent.Connected(flux, this));
            iterator.remove();
            sortConnections = true;
        }
    }

    public void removeConnections() {
        if(toRemove.isEmpty()) {
            return;
        }
        Iterator<IFluxConnector> iterator = toRemove.iterator();
        while(iterator.hasNext()) {
            IFluxConnector flux = iterator.next();
            FluxType.getValidTypes(flux).forEach(t -> ((List<IFluxConnector>) getConnections(t)).removeIf(f -> f == flux));
            iterator.remove();
            sortConnections = true;
        }
    }

    @Override
    public <T extends IFluxConnector> List<T> getConnections(FluxType<T> type) {
        return (List<T>) connections.computeIfAbsent(type, m -> new ArrayList<>());
    }

    @Override
    public void onStartServerTick() {
        List<IFluxConnector> fluxConnectors = getConnections(FluxType.flux);
        fluxConnectors.forEach(f -> f.getTransferHandler().onServerStartTick());
    }

    @Override
    public void onEndServerTick() {
        addConnections();
        removeConnections();
        if(sortConnections) {
            sortConnections();
            sortConnections = false;
        }

        bufferLimiter = 0;

        if(!sortedPoints.isEmpty()) {
            sortedPoints.forEach(g -> g.getConnectors().forEach(p -> bufferLimiter += p.getTransferHandler().removeFromNetwork(Integer.MAX_VALUE, true, true)));
            if (bufferLimiter > 0 && !sortedPlugs.isEmpty()) {
                pointTransferIterator.update(sortedPoints, true);
                plugTransferIterator.update(sortedPlugs, false);
                CYCLE:
                while (pointTransferIterator.hasNext()) {
                    while (plugTransferIterator.hasNext()) {
                        IFluxPlug plug = plugTransferIterator.getCurrentFlux();
                        IFluxPoint point = pointTransferIterator.getCurrentFlux();
                        if(plug.getConnectionType() == point.getConnectionType()) { // Storage have the lowest priority
                            break CYCLE;
                        }
                        long removed = plug.getTransferHandler().addToNetwork(point.getTransferHandler().getRequest(), true);
                        long added = point.getTransferHandler().removeFromNetwork(removed, true, false);
                        if(added > 0) {
                            long actualRemoved = plug.getTransferHandler().addToNetwork(added, false);
                            point.getTransferHandler().removeFromNetwork(actualRemoved, false, false);
                            if(point.getTransferHandler().getRequest() <= 0) {
                                continue CYCLE;
                            }
                        } else {
                            // EU received 4RF at least, prevent dead loop
                            plugTransferIterator.incrementFlux();
                        }
                    }
                    break CYCLE;
                }
            }
        }
        network_stats.getValue().onEndServerTick();
    }

    @Override
    public AccessPermission getMemberPermission(EntityPlayer player) {
        if(FluxConfig.enableSuperAdmin) {
            ISuperAdmin sa = player.getCapability(Capabilities.SUPER_ADMIN, null);
            if(sa != null && sa.getPermission()) {
                return AccessPermission.SUPER_ADMIN;
            }
        }
        return network_players.getValue().stream().collect(Collectors.toMap(NetworkMember::getPlayerUUID, NetworkMember::getPermission)).getOrDefault(EntityPlayer.getUUID(player.getGameProfile()), network_security.getValue().isEncrypted() ? AccessPermission.NONE : AccessPermission.USER);
    }

    @Override
    public void onRemoved() {
        getConnections(FluxType.flux).forEach(flux -> MinecraftForge.EVENT_BUS.post(new FluxConnectionEvent.Disconnected((IFluxConnector) flux, this)));
        connections.clear();
        toAdd.clear();
        toRemove.clear();
        sortedPoints.clear();
        sortedPlugs.clear();
    }

    @Override
    public void queueConnectionAddition(IFluxConnector flux) {
        toAdd.add(flux);
        toRemove.remove(flux);
        removeFromUnloaded(flux);
    }

    @Override
    public void queueConnectionRemoval(IFluxConnector flux, boolean chunkUnload) {
        toRemove.add(flux);
        toAdd.remove(flux);
        if(chunkUnload) {
            addToUnloaded(flux);
        } else {
            removeFromUnloaded(flux);
        }
    }

    private void addToUnloaded(IFluxConnector flux) {
        if(!unloaded_connectors.getValue().stream().anyMatch(f -> f.getCoords().equals(flux.getCoords()))) {
            FluxLiteConnector lite = new FluxLiteConnector(flux);
            unloaded_connectors.getValue().add(lite);
        }
    }

    private void removeFromUnloaded(IFluxConnector flux) {
        unloaded_connectors.getValue().removeIf(f -> f.getCoords().equals(flux.getCoords()));
    }

    @Override
    public void addNewMember(String name) {
        NetworkMember a = NetworkMember.createMemberByUsername(name);
        if(!network_players.getValue().stream().anyMatch(f -> f.getPlayerUUID().equals(a.getPlayerUUID()))) {
            network_players.getValue().add(a);
        }
    }

    @Override
    public void removeMember(UUID uuid) {
        network_players.getValue().removeIf(p -> p.getPlayerUUID().equals(uuid) && !p.getPermission().canDelete());
    }

    @Override
    public Optional<NetworkMember> getValidMember(UUID player) {
        return network_players.getValue().stream().filter(f -> f.getPlayerUUID().equals(player)).findFirst();
    }

    public void markLiteSettingChanged(IFluxConnector flux) {

    }

    private void sortConnections() {
        sortedPlugs.clear();
        sortedPoints.clear();
        List<IFluxPlug> plugs = getConnections(FluxType.plug);
        List<IFluxPoint> points = getConnections(FluxType.point);
        plugs.forEach(p -> PriorityGroup.getOrCreateGroup(p.getPriority(), sortedPlugs).getConnectors().add(p));
        points.forEach(p -> PriorityGroup.getOrCreateGroup(p.getPriority(), sortedPoints).getConnectors().add(p));
        sortedPlugs.sort(Comparator.comparing(p -> -p.getPriority()));
        sortedPoints.sort(Comparator.comparing(p -> -p.getPriority()));
    }
}
