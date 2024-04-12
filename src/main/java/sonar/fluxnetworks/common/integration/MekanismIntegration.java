package sonar.fluxnetworks.common.integration;

import mekanism.api.MekanismAPI;
import sonar.fluxnetworks.FluxNetworks;

public class MekanismIntegration {

    public static void preInit() {
        MekanismAPI.addBoxBlacklistMod(FluxNetworks.MODID);
    }
}
