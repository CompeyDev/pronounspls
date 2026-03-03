package xyz.devcomp.pronounsplus;

import java.util.EnumSet;
import java.util.List;

import net.minecraft.util.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Decoration;
import net.minecraft.text.Style;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.message.MessageType;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.registry.DynamicRegistrySetupCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PronounsPlus implements DedicatedServerModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("Pronouns+");
	public static final FabricLoader LOADER = FabricLoader.getInstance();

	public static MinecraftServer server;

    public static final String MOD_ID = "pronounsplus";
    public static final Identifier PRONOUNS_MESSAGE_TYPE_ID = Identifier.of(MOD_ID, "chat_pronouns_format");

	@Override
	public void onInitializeServer() {
		LOGGER.info("Hello, pronouns!");

        ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
        DynamicRegistrySetupCallback.EVENT.register(registryView -> {
            registryView.getOptional(RegistryKeys.MESSAGE_TYPE).ifPresent(registry -> {
                Registry.register(registry, PRONOUNS_MESSAGE_TYPE_ID, new MessageType(
                    // HACK: Since resources are not loaded on the server and translation is done on the client,
                    // instead of providing a translation key, we provide the literal formatting string which
                    // clients usually fallback to

                    // TODO: We need to actually do translations for pronouns, and allow format configuration

                    new Decoration("%1$s | <%2$s>: %3$s", List.of(Decoration.Parameter.TARGET, Decoration.Parameter.SENDER, Decoration.Parameter.CONTENT), Style.EMPTY),
                    Decoration.ofChat("chat.type.text.narrate") // default narration, unaffected by pronouns
                ));
            });
        });

        // For nametags and virtual team management
        ServerPlayConnectionEvents.JOIN.register(((handler, _sender, s) -> {
            PronounsTeamManager.syncToPlayer(handler.player);
            PronounsTeamManager.setPronouns(handler.player, "they/them", s);
        }));
        ServerPlayConnectionEvents.DISCONNECT.register(((handler, s) -> PronounsTeamManager.removePronouns(handler.player , s)));
	}

    /**
     * Syncs the player list entry for a specific player for all clients. Typically
     * called once the pronouns are updated for a player.
     *
     * @param player the player to update the player list for
     */
    public static void refreshPlayerList(ServerPlayerEntity player) {
        PlayerListS2CPacket packet = new PlayerListS2CPacket(
                EnumSet.of(PlayerListS2CPacket.Action.UPDATE_DISPLAY_NAME),
                List.of(player)
        );

        server.getPlayerManager().sendToAll(packet);
    }
}
