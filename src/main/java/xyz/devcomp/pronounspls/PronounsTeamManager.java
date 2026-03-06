package xyz.devcomp.pronounspls;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.network.packet.s2c.play.TeamS2CPacket;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Decoration;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import org.jetbrains.annotations.Nullable;

/**
 * Manages virtual scoreboard teams for displaying pronouns above player heads.
 * Teams are client-side only and do not pollute the server scoreboard.
 */
public class PronounsTeamManager {

    // Map of player UUID -> their current pronouns source (PronounDB or custom)
    private static final Map<UUID, PronounsSource> playerPronouns = new HashMap<>();

    /**
     * Sets pronouns for a player from a given source and sends translated packets to all connected clients.
     *
     * @param player  the player to set pronouns for
     * @param source  the pronouns source (PronounDB weak ref or custom translation key)
     * @param server  the minecraft server instance
     */
    public static void setPronouns(ServerPlayerEntity player, PronounsSource source, MinecraftServer server) {
        Optional<String> pronounsKey = resolveKey(source);
        if (pronounsKey.isEmpty()) return;

        PronounsPlease.LOGGER.info("Setting pronouns for {} to {}", player.getStringifiedName(), pronounsKey.get());
        removePronouns(player, server);
        playerPronouns.put(player.getUuid(), source);

        for (ServerPlayerEntity recipient : server.getPlayerManager().getPlayerList()) {
            sendTeamPacket(player, pronounsKey.get(), resolveFlag(source).orElse(null), recipient, true);
        }
    }

    /**
     * Removes the pronouns for a player, destroys the virtual team on all clients.
     *
     * @param player the player to remove pronouns for
     * @param server the minecraft server instance
     */
    public static void removePronouns(ServerPlayerEntity player, MinecraftServer server) {
        PronounsPlease.LOGGER.info("Removing pronouns for {}", player.getStringifiedName());

        if (!playerPronouns.containsKey(player.getUuid())) return;
        playerPronouns.remove(player.getUuid());

        String teamName = PronounsPlease.MOD_ID + "_" + player.getUuid();
        Scoreboard tempScoreboard = new Scoreboard();
        Team tempTeam = tempScoreboard.addTeam(teamName);
        server.getPlayerManager().sendToAll(TeamS2CPacket.updateRemovedTeam(tempTeam));
    }

    /**
     * Syncs all present pronouns to a newly joined player, translated into their language.
     *
     * @param recipient the newly joined player to sync to
     * @param server    the minecraft server instance
     */
    public static void syncToPlayer(ServerPlayerEntity recipient, MinecraftServer server) {
        PronounsPlease.LOGGER.info("Syncing virtual teams to player {}", recipient.getStringifiedName());

        for (Map.Entry<UUID, PronounsSource> entry : playerPronouns.entrySet()) {
            resolveKey(entry.getValue()).ifPresent(key -> {
                ServerPlayerEntity target = server.getPlayerManager().getPlayer(entry.getKey());
                if (target != null) {
                    sendTeamPacket(target, key, resolveFlag(entry.getValue()).orElse(null), recipient, true);
                }
            });
        }
    }

    /**
     * Sends a team packet to a specific recipient with pronouns translated into their language.
     * A temporary scoreboard is used per-send to avoid mutating a shared team object, which
     * would cause all recipients to see the last translation written.
     */
    private static void sendTeamPacket(
        ServerPlayerEntity target,
        String pronounsKey,
        @Nullable PronounsPrideFlag prideFlag,
        ServerPlayerEntity recipient,
        boolean withMembers
    ) {
        String teamName = PronounsPlease.MOD_ID + "_" + target.getUuid();
        Scoreboard tempScoreboard = new Scoreboard();
        Team tempTeam = tempScoreboard.addTeam(teamName);

        String translated = PronounsTranslationManager
            .INSTANCE
            .translate(recipient, pronounsKey);

        // TODO: make pride flag application optional and configurable
        Text prefix = prideFlag != null ? prideFlag.apply(translated) : Text.literal(translated).formatted(Formatting.GRAY);
        // MutableText separator = Text.literal("  ").styled(s -> s.withObfuscated(true).withColor(Formatting.BOLD));

        tempTeam.getPlayerList().add(target.getStringifiedName());
        tempTeam.setPrefix(Text.literal("[").append(prefix).append("] "));

        recipient.networkHandler.sendPacket(TeamS2CPacket.updateTeam(tempTeam, withMembers));
    }

    /**
     * Gets the decoration if the source of the pronouns is PronounDB and one is equipped by the user.
     */
    public static Optional<PronounsPrideFlag> getPrideFlag(UUID player) {
        return Optional.ofNullable(playerPronouns.get(player))
            .flatMap(PronounsTeamManager::resolveFlag);
    }

    /**
     * Gets the translation key for the pronouns registered for a player UUID, if present and still alive.
     */
    public static Optional<String> getPronounsKey(UUID player) {
        return Optional.ofNullable(playerPronouns.get(player))
            .flatMap(PronounsTeamManager::resolveKey);
    }

    /**
     * Gets the pronouns key registered for a player entity, if present and still alive.
     */
    public static Optional<String> getPronounsKey(ServerPlayerEntity player) {
        return getPronounsKey(player.getUuid());
    }

    /**
     * Gets the pronouns registered for a player in their language, if present and still alive.
     */
    public static Optional<String> getTranslatedPronouns(ServerPlayerEntity player) {
        return getPronounsKey(player)
            .map(key -> PronounsTranslationManager.INSTANCE.translate(player, key));
    }

    /**
     * Returns whether a player currently has live pronouns set.
     * For PronounDB sources, returns false if the cache entry has been evicted.
     */
    public static boolean hasPronouns(UUID uuid) {
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
