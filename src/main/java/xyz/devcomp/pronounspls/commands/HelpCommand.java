package xyz.devcomp.pronounspls.commands;

import xyz.devcomp.pronounspls.PronounsCommandManager;

import static net.minecraft.commands.Commands.literal;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import java.util.List;
import java.util.Objects;


@PronounsCommandManager.CommandInfo(usage = "/pronounspls help", description = "Show this help message")
public class HelpCommand implements PronounsCommandManager.PronounsCommand {
    @Override
    public void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(literal("help")
            .executes(ctx -> {
                CommandSourceStack source = ctx.getSource();
                List<PronounsCommandManager.CommandInfo> infos = PronounsCommandManager.SUBCOMMANDS.stream()
                    .map(s -> s.getClass().getAnnotation(PronounsCommandManager.CommandInfo.class))
                    .filter(Objects::nonNull)
                    .toList();

                source.sendSuccess(() -> Component.literal("--- ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("Pronouns, Please!").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD))
                        .append(Component.literal(" ---").withStyle(ChatFormatting.GRAY)),
                    false);

                for (PronounsCommandManager.CommandInfo info : infos) {
                    source.sendSuccess(() -> Component.literal(info.usage())
                            .withStyle(ChatFormatting.AQUA)
                            .append(Component.literal(" - ").withStyle(ChatFormatting.GRAY))
                            .append(Component.literal(info.description()).withStyle(ChatFormatting.WHITE)),
                        false);
                }

                return 1;
            })
        );
    }
}
