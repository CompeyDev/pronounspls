package xyz.devcomp.pronounspls.commands;

import xyz.devcomp.pronounspls.PronounsCommandManager;
import xyz.devcomp.pronounspls.PronounsTeamManager;
import xyz.devcomp.pronounspls.PronounsTranslationManager;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import org.jetbrains.annotations.Nullable;

@PronounsCommandManager.CommandInfo(usage = "/pronounspls get [player]", description = "Get your or another player's current pronouns")
public class GetPronounsCommand implements PronounsCommandManager.PronounsCommand {
    @Override
    public void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(literal("get")
            .executes(ctx -> execute(ctx, null))
            .then(argument("player", EntityArgument.player())
                .executes(ctx -> execute(ctx, EntityArgument.getPlayer(ctx, "player")))
            )
        );
    }

    private static int execute(CommandContext<CommandSourceStack> ctx, @Nullable ServerPlayer target) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer viewer = source.getPlayerOrException();
        ServerPlayer subject = target != null ? target : viewer;
        boolean isSelf = subject.getUUID().equals(viewer.getUUID());

        String language = viewer.clientInformation().language();

        PronounsTeamManager.INSTANCE.getPronounsKey(subject).ifPresentOrElse(
            key -> {
                // We want the pronouns in the command runner's, not the player being queried's language
                String translated = PronounsTranslationManager.INSTANCE.translate(language, key);
                source.sendSuccess(
                    () -> PronounsCommandManager.SUCCESS_PREFIX
                        .copy()
                        .append(Component.literal(isSelf ? "Your" : subject.getName().getString() + "'s").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(" pronouns are set to ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(translated).withStyle(ChatFormatting.AQUA)),
                    false
                );
            },

            () -> source.sendSuccess(
                () -> PronounsCommandManager.ERROR_PREFIX
                    .copy()
                    .append(Component.literal(isSelf ? "You have" : subject.getName().getString() + " has").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(" no pronouns set.").withStyle(ChatFormatting.GRAY)),
                false
            )
        );

        return 1;
    }
}
