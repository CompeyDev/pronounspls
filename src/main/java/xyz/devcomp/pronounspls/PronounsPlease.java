package xyz.devcomp.pronounspls;

import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.List;

import net.minecraft.resources.ResourceKey;
import xyz.devcomp.pronounspls.api.PronounDBClient;

import net.minecraft.resources.Identifier;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.ChatTypeDecoration;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.registry.DynamicRegistrySetupCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jetbrains.annotations.Nullable;

public class PronounsPlease implements DedicatedServerModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("PronounsPlease");
	public static final FabricLoader LOADER = FabricLoader.getInstance();

    public static final String MOD_ID = "pronounspls";
    public static final Identifier PRONOUNS_MESSAGE_TYPE_ID = Identifier.fromNamespaceAndPath(MOD_ID, "chat_pronouns_format");
    public static final Duration PRONOUNS_REFRESH_DURATION = Duration.ofHours(1);

    public static MinecraftServer server;
    @Nullable public static PronounDBClient pronoundb;

	@Override
	public void onInitializeServer() {
		LOGGER.info("May I know your pronouns, please?");

        // Register translations lookup resource loader
        ResourceLoader
            .get(PackType.SERVER_DATA)
            .registerReloadListener(PronounsTranslationManager.getFabricId(), PronounsTranslationManager.INSTANCE);

        ServerLifecycleEvents.SERVER_STOPPING.register(PronounsTeamManager.INSTANCE::saveToDisk);
        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            PronounsTeamManager.INSTANCE.loadFromDisk(s);
            if (!s.usesAuthentication()) {
                LOGGER.warn("PronounDB integration does not work for offline mode servers!");
                return;
            }

            server = s;
            pronoundb = PronounDBClient.builder()
                .logger(LOGGER)
                .withCache(PRONOUNS_REFRESH_DURATION, s.getMaxPlayers())
                .customUserAgent(
                    // e.g. pronounspls/0.1.0
                    MOD_ID + "/" + LOADER
                        .getModContainer(MOD_ID)
                        .orElseThrow()
                        .getMetadata()
                        .getVersion()
                        .getFriendlyString()
                )
                .build();
        });

        DynamicRegistrySetupCallback.EVENT.register(registryView -> {
            registryView.getOptional(Registries.CHAT_TYPE).ifPresent(registry -> {
                Registry.register(registry, PRONOUNS_MESSAGE_TYPE_ID, new ChatType(
                    // HACK: Since resources are not loaded on the server and translation is done on the client,
                    // instead of providing a translation key, we provide the literal formatting string which
                    // clients usually fallback to

                    // TODO: Allow format configuration maybe?

                    new ChatTypeDecoration("%1$s • <%2$s>: %3$s", List.of(ChatTypeDecoration.Parameter.TARGET, ChatTypeDecoration.Parameter.SENDER, ChatTypeDecoration.Parameter.CONTENT), Style.EMPTY),
                    ChatTypeDecoration.withSender("chat.type.text.narrate") // default narration, unaffected by pronouns
                ));
            });
        });

        // Virtual teams initialization and cleanup
        ServerPlayConnectionEvents.DISCONNECT.register(((handler, s) -> PronounsTeamManager.INSTANCE.removePronouns(handler.player, s)));
        ServerPlayConnectionEvents.JOIN.register((handler, _packetSender, s) -> {
            if (pronoundb != null) {
                // NOTE: For now, we use only the first pronoun. We need to consider how we should represent
                // things such as she/they
                pronoundb.lookupAsync(PronounDBClient.Platform.MINECRAFT, handler.player.getStringUUID())
                    .thenAccept(pronouns -> pronouns
                        .ifPresent(p -> {
                            s.execute(() -> {
                                PronounsTeamManager.INSTANCE.setPronouns(handler.player, new PronounsSource.PronounDB(new WeakReference<>(p)), server);
                            });
                        }))
                    .exceptionally(e -> {
                        LOGGER.error("Failed to look up pronouns for {} on PronounDB", handler.player.getPlainTextName(), e);
                        return null;
                    });
            }

            s.execute(() -> {
                // Sync any custom pronouns that may have been loaded from disk
                PronounsTeamManager.INSTANCE.syncToPlayer(handler.player, s);
                PronounsTeamManager.INSTANCE.syncToAll(handler.player, s); // FIXME: redundant if setPronouns called
            });
        });

        // Commands!
        CommandRegistrationCallback.EVENT.register(PronounsCommandManager::register);
    }
}
