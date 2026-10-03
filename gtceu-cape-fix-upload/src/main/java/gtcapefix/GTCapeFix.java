package gtcapefix;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(GTCapeFix.MOD_ID)
public class GTCapeFix {

    public static final String MOD_ID = "gtcapefix";

    public GTCapeFix() {
        // The bug is in GTCEu's client-side cape handler, so there is nothing to do on a
        // dedicated server or without GTCEu.
        if (FMLEnvironment.dist == Dist.CLIENT && ModList.get().isLoaded("gtceu")) {
            MinecraftForge.EVENT_BUS.register(CapeRestorer.class);
        }
    }
}
