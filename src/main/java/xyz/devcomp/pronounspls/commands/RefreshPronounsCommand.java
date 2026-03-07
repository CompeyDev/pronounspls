package xyz.devcomp.pronounspls.commands;

import java.lang.ref.WeakReference;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import xyz.devcomp.pronounspls.PronounsCommandManager;
import xyz.devcomp.pronounspls.PronounsPlease;
import xyz.devcomp.pronounspls.PronounsSource;
import xyz.devcomp.pronounspls.PronounsTeamManager;
import xyz.devcomp.pronounspls.api.PronounDBClient;

import static net.minecraft.server.command.CommandManager.literal;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

@PronounsCommandManager.CommandInfo(usage = "/pronounspls refresh", description = "Refresh your pronouns from PronounDB")
public class RefreshPronounsCommand implements PronounsCommandManager.PronounsCommand {
    private static final Duration COOLDOWN_MS = Duration.ofMinutes(5);

    // Map of Player UUID -> last refresh trigger
    private static final Map<UUID, Instant> lastRefresh = new HashMap<>();

    @Override
    public void register(LiteralArgumentBuilder<ServerCommandSource> root) {
        root.then(literal("refresh")
            .executes(ctx -> {
                ServerCommandSource source = ctx.getSource();
                ServerPlayerEntity player = source.getPlayerOrThrow();
                MinecraftServer server = source.getServer();

                Instant now = Instant.now();
                Instant last = Objects.requireNonNullElse(lastRefresh.get(player.getUuid()), Instant.MIN);
                Instant expiry = last.plus(COOLDOWN_MS);

                if (now.isBefore(expiry)) {
                    // Display error if cooldown has not expired
                    Duration remaining = Duration.between(Instant.now(), expiry);
                    long minutes = remaining.toMinutes();
                    long seconds = remaining.toSecondsPart();
                    Text formatted = Text.literal(minutes > 0 ? minutes + "m " + seconds + "s" : seconds + "s").formatted(Formatting.BOLD);

                    source.sendFeedback(
                        () -> PronounsCommandManager.ERROR_PREFIX.copy().append(
                            Text.literal("You can refresh your pronouns again in ").formatted(Formatting.GRAY).append(formatted)
                        ),
                        false
                    );
                    return 1;
                }

                if (PronounsPlease.pronoundb == null) {
                    SetPronounsCommand.error("PronounDB is unavailable in offline mode", null, source);
                    return 1;
                }

                PronounsPlease.pronoundb.invalidate(PronounDBClient.Platform.MINECRAFT, player.getUuidAsString());
                PronounsPlease.pronoundb.lookupAsync(PronounDBClient.Platform.MINECRAFT, player.getUuidAsString())
                    .thenAccept(pronouns -> server.execute(() -> {
                        if (pronouns.isEmpty()) {
                            PronounsTeamManager.INSTANCE.removePronouns(player, server);
                            source.sendFeedback(
                                () -> PronounsCommandManager.SUCCESS_PREFIX.copy().append(
                                    Text.literal("No pronouns found on PronounDB, pronouns removed").formatted(Formatting.GRAY)
                                ),
                                false
                            );
                            return;
                        }

                        pronouns.ifPresent(p -> {
                            // Place user in a cooldown
                            lastRefresh.put(player.getUuid(), Instant.now());

                            PronounsTeamManager.INSTANCE.setPronouns(player, new PronounsSource.PronounDB(new WeakReference<>(p)), server);
                            PronounsTeamManager.INSTANCE.syncToPlayer(player, server);
                            source.sendFeedback(
                                () -> PronounsCommandManager.SUCCESS_PREFIX.copy().append(
                                    Text.literal("Pronouns refreshed from PronounDB")
                                        .formatted(Formatting.GRAY)
                                ),
                                false
                            );
                        });
                    }))
                    .exceptionally(e -> {
                        Text why = Text.literal(e.getCause().getMessage()).formatted(Formatting.BOLD);
                        server.execute(() -> SetPronounsCommand.error("Failed to refresh pronouns from PronounDB: ", why, source));
                        return null;
                    });

                return 1;
            })
        );
    }
}
