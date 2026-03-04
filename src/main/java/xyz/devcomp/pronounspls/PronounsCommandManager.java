package xyz.devcomp.pronounspls;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

import xyz.devcomp.pronounspls.commands.*;

import static net.minecraft.server.command.CommandManager.literal;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.Formatting;
import net.minecraft.text.Text;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

public class PronounsCommandManager {
    public static final Text SUCCESS_PREFIX = Text.literal("✔ ").formatted(Formatting.GREEN);
    public static final Text ERROR_PREFIX = Text.literal("✖ ").formatted(Formatting.RED);

    public interface PronounsCommand {
        void register(LiteralArgumentBuilder<ServerCommandSource> root);
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
        new GetPronounsCommand()
        // TODO: force update / sync command (for pronoundb integration)
    );

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess _registryAccess, CommandManager.RegistrationEnvironment _environment) {
        LiteralArgumentBuilder<ServerCommandSource> root = literal(PronounsPlease.MOD_ID);

        for (PronounsCommand subcommand : SUBCOMMANDS) {
            subcommand.register(root);
        }

        dispatcher.register(root);
    }
}
