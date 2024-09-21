package sonar.fluxnetworks.client;

import com.google.common.collect.Lists;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.color.IBlockColor;
import net.minecraft.client.renderer.color.IItemColor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Tuple;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import sonar.fluxnetworks.api.network.NetworkSettings;
import sonar.fluxnetworks.api.utils.FluxConfigurationType;
import sonar.fluxnetworks.client.gui.basic.GuiFluxCore;
import sonar.fluxnetworks.common.block.BlockFluxCore;
import sonar.fluxnetworks.common.core.FluxUtils;
import sonar.fluxnetworks.common.data.FluxNetworkData;
import sonar.fluxnetworks.common.handler.PacketHandler;
import sonar.fluxnetworks.common.item.ItemConfigurator;
import sonar.fluxnetworks.common.network.PacketColorRequest;
import sonar.fluxnetworks.common.tileentity.TileFluxCore;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Render network color on blocks and items.
 */
public class FluxColorHandler implements IBlockColor, IItemColor {

    public static final FluxColorHandler INSTANCE = new FluxColorHandler();

    public static final int DEFAULT_COLOR = FluxUtils.getIntFromColor(41, 94, 138);
    public static final int NO_NETWORK_COLOR = FluxUtils.getIntFromColor(178, 178, 178);
    public static final Map<Long, Integer> colorCache = new HashMap<>();
    public static final Map<Long, String> nameCache = new HashMap<>();
    private static final List<Long> requests = new ArrayList<>();
    private static final List<Long> sent_requests = new ArrayList<>();

    public static void reset() {
        colorCache.clear();
        nameCache.clear();
        requests.clear();
    }

    public static void loadColorCache(long id, int color) {
        if (id != -1) {
            colorCache.put(id, color);
        }
    }

    public static void loadNameCache(long id, String name) {
        if (id != -1) {
            nameCache.put(id, name);
        }
    }

    public static void placeRequest(long id) {
        if (id != -1 && !requests.contains(id) && !sent_requests.contains(id)) {
            requests.add(id);
        }
    }

    public static int getOrRequestNetworkColor(long id) {
        if (id == -1) {
            return NO_NETWORK_COLOR;
        }
        Integer cached = colorCache.get(id);
        if (cached != null) {
            return cached;
        }
        placeRequest(id);
        return NO_NETWORK_COLOR;
    }

    public static String getOrRequestNetworkName(long id) {
        if (id == -1) {
            return "NONE";
        }
        String cached = nameCache.get(id);
        if (cached != null) {
            return cached;
        }
        placeRequest(id);
        return "WAITING FOR SERVER";
    }

    public static int tickCount;

    public static void sendRequests() {
        if (!requests.isEmpty()) {
            tickCount++;
            if (tickCount > 40) {
                tickCount = 0;
                PacketHandler.network.sendToServer(new PacketColorRequest.ColorRequestMessage(Lists.newArrayList(requests)));
                sent_requests.addAll(requests);
                requests.clear();
            }
        }
    }

    public static void receiveCache(Map<Long, Tuple<Integer, String>> cache) {
        cache.forEach((ID, DETAILS) -> {
            loadColorCache(ID, DETAILS.getFirst());
            loadNameCache(ID, DETAILS.getSecond());
            sent_requests.remove(ID);
            requests.remove(ID);
        });
    }

    @Override
    public int colorMultiplier(IBlockState state, @Nullable IBlockAccess world, @Nullable BlockPos pos, int tintIndex) {
        if (tintIndex == 1 && pos != null && world != null) {
            TileEntity tile = world.getTileEntity(pos);
            if (!state.getValue(BlockFluxCore.CONNECTED)) {
                return NO_NETWORK_COLOR;
            }
            if (tile instanceof TileFluxCore) {
                TileFluxCore t = (TileFluxCore) tile;
                return FluxUtils.getBrighterColor(t.color, 1.2);
            }
            return DEFAULT_COLOR;
        }
        return -1;
    }

    @Override
    public int colorMultiplier(ItemStack stack, int tintIndex) {
        if (tintIndex == 1) {
            if (stack.hasTagCompound() && stack.getTagCompound().getBoolean(FluxUtils.GUI_COLOR)) {
                Gui screen = Minecraft.getMinecraft().currentScreen;
                if (screen instanceof GuiFluxCore) {
                    GuiFluxCore guiFluxCore = (GuiFluxCore) screen;
                    return guiFluxCore.network.isInvalid() ? NO_NETWORK_COLOR : guiFluxCore.network.getSetting(NetworkSettings.NETWORK_COLOR) | 0xff000000;
                }
            }
            NBTTagCompound tag = stack.getSubCompound(FluxUtils.FLUX_DATA);
            if (tag != null) {
                return getOrRequestNetworkColor(tag.getInteger(FluxNetworkData.NETWORK_ID));
            }
            return NO_NETWORK_COLOR;
        }
        return -1;
    }

    public static int colorMultiplierForConfigurator(ItemStack stack, int tintIndex) {
        if (tintIndex == 1) {
            Gui screen = Minecraft.getMinecraft().currentScreen;
            if (screen instanceof GuiFluxCore) {
                GuiFluxCore guiFluxCore = (GuiFluxCore) screen;
                if (guiFluxCore.connector instanceof ItemConfigurator.NetworkConnector) {
                    return guiFluxCore.network.getSetting(NetworkSettings.NETWORK_COLOR);
                }
            }
            NBTTagCompound tag = stack.getSubCompound(FluxUtils.CONFIGS_TAG);
            if (tag != null) {
                return getOrRequestNetworkColor(tag.getLong(FluxConfigurationType.NETWORK.getNBTName()));
            }
            return NO_NETWORK_COLOR;
        }
        return -1;
    }
}
