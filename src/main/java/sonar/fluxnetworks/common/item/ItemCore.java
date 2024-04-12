package sonar.fluxnetworks.common.item;

import net.minecraft.item.Item;
import sonar.fluxnetworks.FluxNetworks;
import sonar.fluxnetworks.common.CommonProxy;
import sonar.fluxnetworks.common.registry.RegistryItems;

public class ItemCore extends Item {

    public ItemCore(String name) {

        setTranslationKey(FluxNetworks.MODID + "." + name.toLowerCase());
        setRegistryName(name.toLowerCase());
        setCreativeTab(CommonProxy.creativeTabs);
        RegistryItems.ITEMS.add(this);
    }

    public void registerModels() {

        FluxNetworks.proxy.registerItemModel(this, 0, "inventory");
    }
}
