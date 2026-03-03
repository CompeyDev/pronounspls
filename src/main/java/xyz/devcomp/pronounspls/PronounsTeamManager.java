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
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Manages virtual scoreboard teams for displaying pronouns above player heads.
 * Teams are client-side only and do not pollute the server scoreboard.
 */
public class PronounsTeamManager {

    // Map of player UUID -> their current pronouns translation key
    private static final Map<UUID, String> playerPronouns = new HashMap<>();

    /**
     * Sets pronouns for a player and sends translated packets to all connected clients.
     *
     * @param player      the player to set pronouns for
     * @param pronounsKey the translation key for the pronouns (e.g. "pronounspls.pronouns.he")
     * @param server      the minecraft server instance
     */
    public static void setPronouns(ServerPlayerEntity player, String pronounsKey, MinecraftServer server) {
        PronounsPlease.LOGGER.info("Setting pronouns for {} to {}", player.getStringifiedName(), pronounsKey);
        removePronouns(player, server);
        playerPronouns.put(player.getUuid(), pronounsKey);

        // Send a translated packet to each connected player
        for (ServerPlayerEntity recipient : server.getPlayerManager().getPlayerList()) {
            sendTeamPacket(player, pronounsKey, recipient, true);
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

        for (Map.Entry<UUID, String> entry : playerPronouns.entrySet()) {
            ServerPlayerEntity target = server.getPlayerManager().getPlayer(entry.getKey());
            if (target != null) {
                sendTeamPacket(target, entry.getValue(), recipient, true);
            }
        }
    }

    /**
     * Sends a team packet to a specific recipient with pronouns translated into their language.
     * A temporary scoreboard is used per-send to avoid mutating a shared team object, which
     * would cause all recipients to see the last translation written.
     */
    private static void sendTeamPacket(ServerPlayerEntity target, String pronounsKey, ServerPlayerEntity recipient, boolean withMembers) {
        String language = recipient.getClientOptions().language();
        String translated = PronounsTranslationManager.INSTANCE.translate(language, pronounsKey);

        // Create a temporary team just for this packet; we don't want to mutate an existing one
        // people with potentially other locales are using
        String teamName = PronounsPlease.MOD_ID + "_" + target.getUuid();
        Scoreboard tempScoreboard = new Scoreboard();
        Team tempTeam = tempScoreboard.addTeam(teamName);
        tempTeam.getPlayerList().add(target.getStringifiedName());
        tempTeam.setSuffix(Text.literal(" [" + translated + "]").formatted(Formatting.GRAY));

        recipient.networkHandler.sendPacket(TeamS2CPacket.updateTeam(tempTeam, withMembers));
    }

    /**
     * Gets the pronouns key registered for a player, if present.
     */
    public static Optional<String> getPronouns(UUID uuid) {
        return Optional.ofNullable(playerPronouns.get(uuid));
    }

    /**
     * Returns whether a player currently has pronouns set.
     */
    public static boolean hasPronouns(UUID uuid) {
        return playerPronouns.containsKey(uuid);
    }
}
