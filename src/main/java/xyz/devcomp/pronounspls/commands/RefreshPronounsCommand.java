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

import static net.minecraft.commands.Commands.literal;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

@PronounsCommandManager.CommandInfo(usage = "/pronounspls refresh", description = "Refresh your pronouns from PronounDB")
public class RefreshPronounsCommand implements PronounsCommandManager.PronounsCommand {
    private static final Duration COOLDOWN_MS = Duration.ofMinutes(5);

    // Map of Player UUID -> last refresh trigger
    private static final Map<UUID, Instant> lastRefresh = new HashMap<>();

    @Override
    public void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(literal("refresh")
            .executes(ctx -> {
                CommandSourceStack source = ctx.getSource();
                ServerPlayer player = source.getPlayerOrException();
                MinecraftServer server = source.getServer();

                Instant now = Instant.now();
                Instant last = Objects.requireNonNullElse(lastRefresh.get(player.getUUID()), Instant.MIN);
                Instant expiry = last.plus(COOLDOWN_MS);

                if (now.isBefore(expiry)) {
                    // Display error if cooldown has not expired
                    Duration remaining = Duration.between(Instant.now(), expiry);
                    long minutes = remaining.toMinutes();
                    long seconds = remaining.toSecondsPart();
                    Component formatted = Component.literal(minutes > 0 ? minutes + "m " + seconds + "s" : seconds + "s").withStyle(ChatFormatting.BOLD);

                    source.sendFailure(
                        PronounsCommandManager.ERROR_PREFIX.copy().append(
                            Component.literal("You can refresh your pronouns again in ").withStyle(ChatFormatting.GRAY).append(formatted)
                        )
                    );
                    return 1;
                }

                if (PronounsPlease.pronoundb == null) {
                    SetPronounsCommand.error("PronounDB is unavailable in offline mode", null, source);
                    return 1;
                }

                PronounsPlease.pronoundb.invalidate(PronounDBClient.Platform.MINECRAFT, player.getStringUUID());
                PronounsPlease.pronoundb.lookupAsync(PronounDBClient.Platform.MINECRAFT, player.getStringUUID())
                    .thenAccept(pronouns -> server.execute(() -> {
                        if (pronouns.isEmpty()) {
                            PronounsTeamManager.INSTANCE.removePronouns(player, server);
                            source.sendSuccess(
                                () -> PronounsCommandManager.SUCCESS_PREFIX.copy().append(
                                    Component.literal("No pronouns found on PronounDB, pronouns removed").withStyle(ChatFormatting.GRAY)
                                ),
                                false
                            );
                            return;
                        }

                        pronouns.ifPresent(p -> {
                            // Place user in a cooldown
                            lastRefresh.put(player.getUUID(), Instant.now());

                            PronounsTeamManager.INSTANCE.setPronouns(player, new PronounsSource.PronounDB(new WeakReference<>(p)), server);
                            PronounsTeamManager.INSTANCE.syncToPlayer(player, server);
                            source.sendSuccess(
                                () -> PronounsCommandManager.SUCCESS_PREFIX.copy().append(
                                    Component.literal("Pronouns refreshed from PronounDB").withStyle(ChatFormatting.GRAY)
                                ),
                                false
                            );
                        });
                    }))
                    .exceptionally(e -> {
                        Component why = Component.literal(e.getCause().getMessage()).withStyle(ChatFormatting.BOLD);
                        server.execute(() -> SetPronounsCommand.error("Failed to refresh pronouns from PronounDB: ", why, source));
                        return null;
                    });

                return 1;
            })
        );
    }
}
