package gtcapefix;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Works around GregTech-Modern issue #3866.
 * <p>
 * GTCEu's {@code RenderPlayerEvent.Pre} handler remembers each player's Mojang cape the first
 * time the player is drawn and never re-reads it. If that first draw happens before the cape has
 * downloaded (third person on join, or a shaderpack drawing the player into its shadow map), it
 * remembers "no cape" and writes that over the real cape every frame for the rest of the session.
 * <p>
 * This class listens to the same event on both sides of GTCEu's handler (which runs at NORMAL
 * priority): before it, to record the real cape whenever vanilla has supplied one; after it, to
 * put that cape back when GTCEu left none and the player has no GTCEu cape selected.
 * <p>
 * GTCEu is reached by reflection so the mod builds without GTCEu and works across GTCEu versions
 * that moved the handler between classes.
 */
public final class CapeRestorer {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final MinecraftProfileTexture.Type CAPE = MinecraftProfileTexture.Type.CAPE;
    private static final ResourceLocation UNKNOWN_GTCEU_CAPE = new ResourceLocation("gtceu", "unknown");

    /** Last Mojang (non-GTCEu) cape seen for each player this connection. */
    private static final Map<UUID, ResourceLocation> REAL_CAPES = new HashMap<>();

    private static boolean resolved;
    private static boolean disabled;
    /** {@code AbstractClientPlayer#gtceu$getPlayerInfo()}, added by GTCEu's accessor mixin. */
    private static Method getPlayerInfo;
    /** {@code PlayerInfo#getTextureLocations()}, added by GTCEu's accessor mixin. */
    private static Method getTextureLocations;
    /** {@code CapeRegistry#getPlayerCapeTexture(UUID)}. */
    private static Method getPlayerCapeTexture;
    /** {@code CapeRegistry.ALL_CAPES}: cape id to texture. */
    private static Map<?, ?> allCapes;

    private CapeRestorer() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beforeGTCEu(RenderPlayerEvent.Pre event) {
        Map<MinecraftProfileTexture.Type, ResourceLocation> textures = textures(event.getEntity());
        if (textures == null) return;

        ResourceLocation current = textures.get(CAPE);
        if (current != null && !allCapes.containsValue(current)) {
            REAL_CAPES.put(event.getEntity().getUUID(), current);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void afterGTCEu(RenderPlayerEvent.Pre event) {
        UUID uuid = event.getEntity().getUUID();
        ResourceLocation realCape = REAL_CAPES.get(uuid);
        if (realCape == null) return;

        Map<MinecraftProfileTexture.Type, ResourceLocation> textures = textures(event.getEntity());
        if (textures == null) return;

        if (textures.get(CAPE) == null && gtceuCape(uuid) == null) {
            textures.put(CAPE, realCape);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        // PlayerInfo is rebuilt on every join, so pick up cape changes next time.
        REAL_CAPES.clear();
    }

    @SuppressWarnings("unchecked")
    private static Map<MinecraftProfileTexture.Type, ResourceLocation> textures(Player player) {
        if (!(player instanceof AbstractClientPlayer) || !resolve()) return null;
        try {
            Object info = getPlayerInfo.invoke(player);
            if (info == null) return null;
            return (Map<MinecraftProfileTexture.Type, ResourceLocation>) getTextureLocations.invoke(info);
        } catch (ReflectiveOperationException | RuntimeException e) {
            disable(e);
            return null;
        }
    }

    private static ResourceLocation gtceuCape(UUID uuid) {
        try {
            return (ResourceLocation) getPlayerCapeTexture.invoke(null, uuid);
        } catch (ReflectiveOperationException | RuntimeException e) {
            disable(e);
            // Assume a GTCEu cape so we never override one by mistake.
            return UNKNOWN_GTCEU_CAPE;
        }
    }

    /** Looks up the GTCEu members on first use, after all mixins have been applied. */
    private static boolean resolve() {
        if (disabled) return false;
        if (resolved) return true;
        try {
            getPlayerInfo = AbstractClientPlayer.class.getMethod("gtceu$getPlayerInfo");
            getTextureLocations = PlayerInfo.class.getMethod("getTextureLocations");
            Class<?> registry = Class.forName("com.gregtechceu.gtceu.api.cosmetics.CapeRegistry");
            getPlayerCapeTexture = registry.getMethod("getPlayerCapeTexture", UUID.class);
            allCapes = (Map<?, ?>) registry.getField("ALL_CAPES").get(null);
            resolved = true;
            LOGGER.info("GTCEu: Modern Cape Fix active");
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            disable(e);
            return false;
        }
    }

    private static void disable(Throwable e) {
        if (!disabled) {
            disabled = true;
            LOGGER.warn("GTCEu: Modern Cape Fix disabled: this GTCEu version doesn't match what it expects", e);
        }
    }
}
