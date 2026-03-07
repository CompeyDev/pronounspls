package xyz.devcomp.pronounspls.commands;

import xyz.devcomp.pronounspls.PronounsCommandManager;
import xyz.devcomp.pronounspls.PronounsTeamManager;
import xyz.devcomp.pronounspls.PronounsTranslationManager;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import org.jetbrains.annotations.Nullable;

@PronounsCommandManager.CommandInfo(usage = "/pronounspls get [player]", description = "Get your or another player's current pronouns")
public class GetPronounsCommand implements PronounsCommandManager.PronounsCommand {
    @Override
    public void register(LiteralArgumentBuilder<ServerCommandSource> root) {
        root.then(literal("get")
            .executes(ctx -> execute(ctx, null))
            .then(argument("player", EntityArgumentType.player())
                .executes(ctx -> execute(ctx, EntityArgumentType.getPlayer(ctx, "player")))
            )
        );
    }

    private static int execute(CommandContext<ServerCommandSource> ctx, @Nullable ServerPlayerEntity target) throws CommandSyntaxException {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity viewer = source.getPlayerOrThrow();
        ServerPlayerEntity subject = target != null ? target : viewer;
        boolean isSelf = subject.getUuid().equals(viewer.getUuid());

        String language = viewer.getClientOptions().language();

        PronounsTeamManager.INSTANCE.getPronounsKey(subject).ifPresentOrElse(
            key -> {
                // We want the pronouns in the command runner's, not the player being queried's language
                String translated = PronounsTranslationManager.INSTANCE.translate(language, key);
                source.sendFeedback(
                    () -> PronounsCommandManager.SUCCESS_PREFIX
                        .copy()
                        .append(Text.literal(isSelf ? "Your" : subject.getName().getString() + "'s").formatted(Formatting.GRAY))
                        .append(Text.literal(" pronouns are set to ").formatted(Formatting.GRAY))
                        .append(Text.literal(translated).formatted(Formatting.AQUA)),
                    false
                );
            },

            () -> source.sendFeedback(
                () -> PronounsCommandManager.ERROR_PREFIX
                    .copy()
                    .append(Text.literal(isSelf ? "You have" : subject.getName().getString() + " has").formatted(Formatting.GRAY))
                    .append(Text.literal(" no pronouns set.").formatted(Formatting.GRAY)),
                false
            )
        );

        return 1;
    }
}
