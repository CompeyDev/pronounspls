package xyz.devcomp.pronounspls.commands;

import java.util.Arrays;
import java.util.List;

import xyz.devcomp.pronounspls.PronounsCommandManager;
import xyz.devcomp.pronounspls.PronounsTeamManager;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.Formatting;
import net.minecraft.text.Text;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.Codec;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;

// TODO: translations for command feedback

enum PronounKey implements StringIdentifiable {
    HE("pronounspls.pronouns.he"),
    SHE("pronounspls.pronouns.she"),
    THEY("pronounspls.pronouns.they"),
    IT("pronounspls.pronouns.it"),
    ANY("pronounspls.pronouns.any"),
    ASK("pronounspls.pronouns.ask"),
    AVOID("pronounspls.pronouns.avoid"),
    OTHER("pronounspls.pronouns.other");

    private final String key;
    public static final Codec<PronounKey> CODEC = StringIdentifiable.createCodec(PronounKey::values);

    PronounKey(String key) { this.key = key; }

    @Override
    public String asString() { return name().toLowerCase(); }

    public String getTranslationKey() { return key; }
}

class PronounKeyArgumentType {
    private static final DynamicCommandExceptionType INVALID_PRONOUN = new DynamicCommandExceptionType(
        value -> PronounsCommandManager.ERROR_PREFIX.copy().append(
            Text.literal("Unknown pronoun ")
                .formatted(Formatting.GRAY)
                .append(Text.literal(value.toString()).formatted(Formatting.AQUA))
        )
    );

    private static final List<String> PRONOUN_KEYS = Arrays.stream(PronounKey.values())
        .map(PronounKey::asString)
        .toList();

    public static RequiredArgumentBuilder<ServerCommandSource, String> pronounArgument(String name) {
        return argument(name, StringArgumentType.word())
            .suggests((ctx, builder) -> {
                PRONOUN_KEYS.forEach(builder::suggest);
                return builder.buildFuture();
            });
    }

    public static PronounKey getPronounKey(CommandContext<ServerCommandSource> ctx, String name) throws CommandSyntaxException {
        String value = StringArgumentType.getString(ctx, name);
        return Arrays.stream(PronounKey.values())
            .filter(k -> k.asString().equals(value))
            .findFirst()
            .orElseThrow(() -> INVALID_PRONOUN.create(value));
    }
}

@PronounsCommandManager.CommandInfo(usage = "/pronounspls set pronoun <pronoun>", description = "Set your pronouns")
public class SetPronounsCommand implements PronounsCommandManager.PronounsCommand {
    @Override
    public void register(LiteralArgumentBuilder<ServerCommandSource> root) {
        root.then(literal("set")
            .then(PronounKeyArgumentType.pronounArgument("pronoun")
                .executes(ctx -> {
                    ServerCommandSource ctxSource = ctx.getSource();
                    ServerPlayerEntity player = ctxSource.getPlayerOrThrow();
                    MinecraftServer server = ctxSource.getServer();
                    PronounKey key = PronounKeyArgumentType.getPronounKey(ctx, "pronoun");

                    // Update pronouns in virtual team -- player list and chat both use it for fetching pronouns
                    PronounsTeamManager.setPronouns(player, key.getTranslationKey(), server);
                    PronounsTeamManager.syncToPlayer(player, server);

                    ctxSource.sendFeedback(
                        () -> PronounsCommandManager.SUCCESS_PREFIX.copy().append(
                            Text.literal("Pronouns set to ")
                                .formatted(Formatting.GRAY)
                                .append(Text.literal(key.asString()).formatted(Formatting.AQUA, Formatting.ITALIC))
                        ),
                        false
                    );

                    return 1;
                })
            )
        );
    }
}
