package xyz.devcomp.pronounspls;

import java.io.IOException;
import java.util.*;

import xyz.devcomp.pronounspls.codec.PronounsSourceCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.PlayerTeam;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.jetbrains.annotations.Nullable;

/**
 * Manages virtual scoreboard teams for displaying pronouns above player heads.
 * Teams are client-side only and do not pollute the server scoreboard.
 */
public class PronounsTeamManager extends PronounsPersistable {
    public static final PronounsTeamManager INSTANCE = new PronounsTeamManager();
    private static final PronounsSourceCodec CODEC = PronounsSourceCodec.INSTANCE;

    // Map of player UUID -> their current pronouns source (PronounDB or custom)
    private final Map<UUID, PronounsSource> playerPronouns = new HashMap<>();
    private final Set<UUID> trackedPets = new HashSet<>();

    private PronounsTeamManager() {
        super("pronouns.json");
    }

    @Override
    public JsonElement save() {
        JsonArray arr = new JsonArray();
        for (Map.Entry<UUID, PronounsSource> entry : playerPronouns.entrySet()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("uuid", entry.getKey().toString());
            obj.add("source", CODEC.serialize(entry.getValue()));
            arr.add(obj);
        }
        return arr;
    }

    @Override
    public void load(JsonElement json) throws IOException {
        for (JsonElement el : json.getAsJsonArray()) {
            JsonObject obj = el.getAsJsonObject();
            UUID uuid = UUID.fromString(obj.get("uuid").getAsString());
            playerPronouns.put(uuid, CODEC.deserialize(obj.get("source")));
        }

        PronounsPlease.LOGGER.info("Loaded pronouns for {} players", playerPronouns.size());
    }

    /**
     * Sets pronouns for a player from a given source and sends translated packets to all connected clients.
     *
     * @param player the player to set pronouns for
     * @param source the pronouns source (PronounDB weak ref or custom translation key)
     * @param server the minecraft server instance
     */
    public void setPronouns(ServerPlayer player, PronounsSource source, MinecraftServer server) {
        Optional<String> pronounsKey = resolveKey(source);
        if (pronounsKey.isEmpty()) return;

        PronounsPlease.LOGGER.info("Setting pronouns for {} to {}", player.getPlainTextName(), pronounsKey.get());
        removePronouns(player, server);
        playerPronouns.put(player.getUUID(), source);
        saveToDisk(server); // FIXME: IO call which can be overhead with large simultaneous joins, do async debounce?
        syncToAll(player, server);
    }

    /**
     * Removes the pronouns for a player, destroys the virtual team on all clients.
     *
     * @param player the player to remove pronouns for
     * @param server the minecraft server instance
     */
    public void removePronouns(ServerPlayer player, MinecraftServer server) {
        PronounsPlease.LOGGER.info("Removing pronouns for {}", player.getPlainTextName());
        if (!playerPronouns.containsKey(player.getUUID())) return;

        String teamName = PronounsPlease.MOD_ID + "_" + player.getUUID();
        Scoreboard tempScoreboard = new Scoreboard();
        PlayerTeam tempTeam = tempScoreboard.addPlayerTeam(teamName);
        server.getPlayerList().broadcastAll(ClientboundSetPlayerTeamPacket.createRemovePacket(tempTeam));
    }

    /**
     * Syncs all present pronouns to a newly joined player, translated into their language.
     *
     * @param recipient the newly joined player to sync to
     * @param server    the minecraft server instance
     */
    public void syncToPlayer(ServerPlayer recipient, MinecraftServer server) {
        PronounsPlease.LOGGER.info("Syncing virtual teams to player {}", recipient.getPlainTextName());

        for (Map.Entry<UUID, PronounsSource> entry : playerPronouns.entrySet()) {
            resolveKey(entry.getValue()).ifPresent(key -> {
                // The joining player this was called for has not been placed as an entity
                // and so their entry returns null, but we still must send the packets to
                // them so that they can see their own pronouns
                ServerPlayer target = entry.getKey().equals(recipient.getUUID())
                    ? recipient
                    : server.getPlayerList().getPlayer(entry.getKey());

                if (target != null) {
                    sendTeamPacket(target, key, resolveFlag(entry.getValue()).orElse(null), recipient);
                }
            });
        }

        for (UUID petUuid : trackedPets) {
            assignPetDummyTeam(petUuid.toString(), recipient);
        }
    }

    /**
     * Syncs a player's pronouns to all connected clients.
     *
     * <p>This does the opposite of what {@link #syncToPlayer(ServerPlayer, MinecraftServer)},
     * does. Typically called when a player joins to ensure all existing clients
     * see their pronouns.
     *
     * <p><b>NOTE</b>: {@link #setPronouns(ServerPlayer, PronounsSource, MinecraftServer)}
     * already calls this method to force a sync after setting the pronouns.
     *
     * @param player the player whose pronouns should be synced
     * @param server the minecraft server instance
     */
    public void syncToAll(ServerPlayer player, MinecraftServer server) {
        Optional<String> key = getPronounsKey(player);
        if (key.isEmpty()) return;

        for (ServerPlayer recipient : server.getPlayerList().getPlayers()) {
            sendTeamPacket(player, key.get(), resolveFlag(playerPronouns.get(player.getUUID())).orElse(null), recipient);
        }
    }

    /**
     * Sends a team packet to a specific recipient with pronouns translated into their language.
     * A temporary scoreboard is used per-send to avoid mutating a shared team object, which
     * would cause all recipients to see the last translation written.
     */
    private void sendTeamPacket(
        ServerPlayer target,
        String pronounsKey,
        @Nullable PronounsPrideFlag prideFlag,
        ServerPlayer recipient
    ) {
        String teamName = PronounsPlease.MOD_ID + "_" + target.getUUID();
        Scoreboard tempScoreboard = new Scoreboard();
        PlayerTeam tempTeam = tempScoreboard.addPlayerTeam(teamName);

        String translated = PronounsTranslationManager
            .INSTANCE
            .translate(recipient, pronounsKey);

        // TODO: make pride flag application optional and configurable
        Component prefix = prideFlag != null ? prideFlag.apply(translated) : Component.literal(translated).withStyle(ChatFormatting.GRAY);
        // MutableText separator = Text.literal("  ").styled(s -> s.withObfuscated(true).withColor(Formatting.BOLD));

        tempTeam.getPlayers().add(target.getPlainTextName());
        tempTeam.setPlayerPrefix(Component.literal("[").append(prefix).append("] "));

        recipient.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(tempTeam, true));
    }

    /**
     * Registers a tamed entity as needing a dummy team assignment to prevent
     * it from inheriting its owner's pronouns team prefix on clients.
     *
     * @param entityUuid the UUID of the tamed entity
     */
    public void trackPet(UUID entityUuid) {
        trackedPets.add(entityUuid);
    }

    /**
     * Unregisters a tamed entity from dummy team tracking, typically called
     * when the entity is no longer tracked by any player or becomes untamed.
     *
     * @param entityUuid the UUID of the tamed entity
     */
    public void untrackPet(UUID entityUuid) {
        trackedPets.remove(entityUuid);
    }

    /**
     * Assigns a tamed entity to its own empty dummy team to prevent it from
     * inheriting its owner's pronouns team prefix.
     *
     * @param entityUuid the tamed entity's UUID string (from getNameForScoreboard)
     * @param recipient  the client to send the packets to
     */
    public void assignPetDummyTeam(String entityUuid, ServerPlayer recipient) {
        String dummyTeamName = PronounsPlease.MOD_ID + "_pet_" + entityUuid;
        Scoreboard tempScoreboard = new Scoreboard();
        PlayerTeam dummyTeam = tempScoreboard.addPlayerTeam(dummyTeamName);

        recipient.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(dummyTeam, true));
        recipient.connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(dummyTeam, entityUuid, ClientboundSetPlayerTeamPacket.Action.ADD));
    }

    /**
     * Removes the dummy team assigned to a tamed entity.
     *
     * @param entityUuid the tamed entity's UUID string
     * @param recipient  the client to send the packet to
     */
    public void removePetDummyTeam(String entityUuid, ServerPlayer recipient) {
        String dummyTeamName = PronounsPlease.MOD_ID + "_pet_" + entityUuid;
        Scoreboard tempScoreboard = new Scoreboard();
        PlayerTeam dummyTeam = tempScoreboard.addPlayerTeam(dummyTeamName);

        recipient.connection.send(ClientboundSetPlayerTeamPacket.createRemovePacket(dummyTeam));
    }

    /**
     * Gets the decoration if the source of the pronouns is PronounDB and one is equipped by the user.
     */
    public Optional<PronounsPrideFlag> getPrideFlag(UUID player) {
        return Optional.ofNullable(playerPronouns.get(player))
            .flatMap(PronounsTeamManager::resolveFlag);
    }

    /**
     * Gets the translation key for the pronouns registered for a player UUID, if present and still alive.
     */
    public Optional<String> getPronounsKey(UUID player) {
        return Optional.ofNullable(playerPronouns.get(player))
            .flatMap(PronounsTeamManager::resolveKey);
    }

    /**
     * Gets the pronouns key registered for a player entity, if present and still alive.
     */
    public Optional<String> getPronounsKey(ServerPlayer player) {
        return getPronounsKey(player.getUUID());
    }

    /**
     * Gets the pronouns registered for a player in their language, if present and still alive.
     */
    public Optional<String> getTranslatedPronouns(ServerPlayer player) {
        return getPronounsKey(player)
            .map(key -> PronounsTranslationManager.INSTANCE.translate(player, key));
    }

    /**
     * Returns whether a player currently has live pronouns set.
     * For PronounDB sources, returns false if the cache entry has been evicted.
     */
    public boolean hasPronouns(UUID uuid) {
        return Optional.ofNullable(playerPronouns.get(uuid))
            .flatMap(PronounsTeamManager::resolveKey)
            .isPresent();
    }

    private static Optional<String> resolveKey(PronounsSource source) {
        return switch (source) {
            case PronounsSource.Custom c -> Optional.of(c.pronounsKey());
            case PronounsSource.PronounDB p -> p.resolve();
        };
    }

    private static Optional<PronounsPrideFlag> resolveFlag(PronounsSource source) {
        return source instanceof PronounsSource.PronounDB s
            ? s.getDecoration().flatMap(PronounsPrideFlag::fromDecoration)
            : Optional.empty();
    }
}
