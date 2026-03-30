package xyz.devcomp.pronounspls;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

import xyz.devcomp.pronounspls.commands.*;

import static net.minecraft.commands.Commands.literal;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

public class PronounsCommandManager {
    public static final Component SUCCESS_PREFIX = Component.literal("✔ ").withStyle(ChatFormatting.GREEN);
    public static final Component ERROR_PREFIX   = Component.literal("✖ ").withStyle(ChatFormatting.RED);

    public interface PronounsCommand {
        void register(LiteralArgumentBuilder<CommandSourceStack> root);
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface CommandInfo {
        String usage();
        String description();
    }

    public static final List<PronounsCommand> SUBCOMMANDS = List.of(
        new HelpCommand(),
        new SetPronounsCommand(),
        new GetPronounsCommand(),
        new RefreshPronounsCommand()
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext _ctx, Commands.CommandSelection _selection) {
        LiteralArgumentBuilder<CommandSourceStack> root = literal(PronounsPlease.MOD_ID);

        for (PronounsCommand subcommand : SUBCOMMANDS) {
            subcommand.register(root);
        }

        dispatcher.register(root);
    }
}
