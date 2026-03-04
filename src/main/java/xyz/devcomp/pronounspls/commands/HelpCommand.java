package xyz.devcomp.pronounspls.commands;

import xyz.devcomp.pronounspls.PronounsPlease;
import xyz.devcomp.pronounspls.PronounsCommandManager;

import static net.minecraft.server.command.CommandManager.literal;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.Formatting;
import net.minecraft.text.Text;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import java.util.List;
import java.util.Objects;


@PronounsCommandManager.CommandInfo(usage = "/pronounspls help", description = "Show this help message")
public class HelpCommand implements PronounsCommandManager.PronounsCommand {
    @Override
    public void register(LiteralArgumentBuilder<ServerCommandSource> root) {
        root.then(literal("help")
            .executes(ctx -> {
                ServerCommandSource source = ctx.getSource();

                List<PronounsCommandManager.CommandInfo> infos = PronounsCommandManager.SUBCOMMANDS.stream()
                    .map(s -> s.getClass().getAnnotation(PronounsCommandManager.CommandInfo.class))
                    .filter(Objects::nonNull)
                    .toList();

                int maxLength = infos.stream()
                    .mapToInt(i -> i.usage().length())
                    .max()
                    .orElse(0);

                source.sendFeedback(() -> Text.literal("--- ")
                        .formatted(Formatting.GRAY)
                        .append(Text.literal("Pronouns, Please!").formatted(Formatting.GREEN, Formatting.BOLD))
                        .append(Text.literal(" ---").formatted(Formatting.GRAY)),
                    false);

                for (PronounsCommandManager.CommandInfo info : infos) {
                    String padded = String.format("%-" + maxLength + "s", info.usage());
                    source.sendFeedback(() -> Text.literal(padded)
                            .formatted(Formatting.AQUA)
                            .append(Text.literal(" - ").formatted(Formatting.GRAY))
                            .append(Text.literal(info.description()).formatted(Formatting.WHITE)),
                        false);
                }

                return 1;
            })
        );
    }
}
