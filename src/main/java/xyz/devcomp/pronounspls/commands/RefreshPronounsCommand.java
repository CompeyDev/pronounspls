package xyz.devcomp.pronounspls.commands;

import java.lang.ref.WeakReference;

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
    @Override
    public void register(LiteralArgumentBuilder<ServerCommandSource> root) {
        root.then(literal("refresh")
            .executes(ctx -> {
                ServerCommandSource source = ctx.getSource();
                ServerPlayerEntity player = source.getPlayerOrThrow();
                MinecraftServer server = source.getServer();

                if (PronounsPlease.pronoundb == null) {
                    SetPronounsCommand.error("PronounDB is unavailable in offline mode", null, source);
                    return 1;
                }

                PronounsPlease.pronoundb.invalidate(PronounDBClient.Platform.MINECRAFT, player.getUuid().toString());
                PronounsPlease.pronoundb.lookupAsync(PronounDBClient.Platform.MINECRAFT, player.getUuid().toString())
                    .thenAccept(pronouns -> server.execute(() -> {
                        if (pronouns.isEmpty()) {
                            PronounsTeamManager.removePronouns(player, server);
                            source.sendFeedback(
                                () -> PronounsCommandManager.SUCCESS_PREFIX.copy().append(
                                    Text.literal("No pronouns found on PronounDB, pronouns removed").formatted(Formatting.GRAY)
                                ),
                                false
                            );
                            return;
                        }

                        pronouns.ifPresent(p -> {
                            PronounsTeamManager.setPronouns(player, new PronounsSource.PronounDB(new WeakReference<>(p)), server);
                            PronounsTeamManager.syncToPlayer(player, server);
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
