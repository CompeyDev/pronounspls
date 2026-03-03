package xyz.devcomp.pronounsplus;

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

    // One fake scoreboard used purely to construct Team objects for packets
    private static final Scoreboard FAKE_SCOREBOARD = new Scoreboard();

    // Map of player UUID -> their current virtual team
    private static final Map<UUID, Team> virtualTeams = new HashMap<>();

    /**
     * Sets pronouns for a player and requests an update for all connected clients.
     *
     * @param player  the player to set pronouns for
     * @param pronouns the pronouns string
     * @param server  the minecraft server instance
     */
    public static void setPronouns(ServerPlayerEntity player, String pronouns, MinecraftServer server) {
        UUID uuid = player.getUuid();

        // Remove existing virtual team if present
        removePronouns(player, server);

        // Create a team and add player to it
        String teamName = PronounsPlus.MOD_ID + "_" + uuid.toString();
        Team team = FAKE_SCOREBOARD.addTeam(teamName);
        team.setSuffix(Text.literal(" [" + pronouns + "]").formatted(Formatting.GRAY));

        team.getPlayerList().add(player.getStringifiedName()); // FIXME: does this work?
        virtualTeams.put(uuid, team);

        // Send sync packet to all clients
        TeamS2CPacket packet = TeamS2CPacket.updateTeam(team, true);
        server.getPlayerManager().sendToAll(packet);
    }

    /**
     * Removes the pronouns for a player, destroys the virtual team, and requests an update
     * for all connected clients.
     *
     * @param player the player to remove pronouns for
     * @param server the minecraft server instance
     */
    public static void removePronouns(ServerPlayerEntity player, MinecraftServer server) {
        UUID uuid = player.getUuid();
        Team existingTeam = virtualTeams.remove(uuid);
        if (existingTeam != null) {
            TeamS2CPacket removePacket = TeamS2CPacket.updateRemovedTeam(existingTeam);
            server.getPlayerManager().sendToAll(removePacket);
            FAKE_SCOREBOARD.removeTeam(existingTeam);
        }
    }

    /**
     * Requests an update for a client to sync all present pronouns for other players.
     * Usually called when a new client connects to the server.
     *
     * @param player the newly joined player to sync to
     */
    public static void syncToPlayer(ServerPlayerEntity player) {
        for (Team team : virtualTeams.values()) {
            player.networkHandler.sendPacket(TeamS2CPacket.updateTeam(team, true));
        }
    }

    /**
     * Gets the pronouns registered for a player, if present, or null.
     */
    public static Optional<String> getPronouns(UUID uuid) {
        Team team = virtualTeams.get(uuid);
        if (team == null) return Optional.empty();

        // Pronouns are wrapped in square brackets, extract it
        String suffix = team.getSuffix().getString();
        if (suffix.startsWith(" [") && suffix.endsWith("]")) {
            return Optional.of(suffix.substring(2, suffix.length() - 1));
        }
        return Optional.empty();
    }

    /**
     * Returns whether a player currently has pronouns set.
     */
    public static boolean hasPronouns(UUID uuid) {
        return virtualTeams.containsKey(uuid);
    }
}
