package xyz.devcomp.pronounspls.commands;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;

import xyz.devcomp.pronounspls.PronounsPlease;
import xyz.devcomp.pronounspls.PronounsCommandManager;
import xyz.devcomp.pronounspls.PronounsSource;
import xyz.devcomp.pronounspls.api.PronounDBClient;
import xyz.devcomp.pronounspls.PronounsTeamManager;
import xyz.devcomp.pronounspls.PronounsTranslationManager;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.Formatting;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.Codec;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;

import org.jetbrains.annotations.Nullable;

// TODO: translations for command feedback

enum PronounKey implements StringIdentifiable {
    HE("pronounspls.pronouns.he"),
    SHE("pronounspls.pronouns.she"),
    THEY("pronounspls.pronouns.they"),
    IT("pronounspls.pronouns.it"),
    ANY("pronounspls.pronouns.any"),
    ASK("pronounspls.pronouns.ask"),
    AVOID("pronounspls.pronouns.avoid"),
    OTHER("pronounspls.pronouns.other"),
    PRONOUNDB(null);

    private final String key;
    public static final Codec<PronounKey> CODEC = StringIdentifiable.createCodec(PronounKey::values);

    PronounKey(String key) { this.key = key; }

    @Override
    public String asString() { return name().toLowerCase(); }

    public String getTranslationKey() { return key; }
}

class PronounKeyArgumentType {
    private static final DynamicCommandExceptionType INVALID_PRONOUN = new DynamicCommandExceptionType(
        value -> SetPronounsCommand.error(
            "Unknown pronoun ",
            Text.literal(value.toString()).formatted(Formatting.AQUA),
            null
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
                    ServerCommandSource source = ctx.getSource();
                    ServerPlayerEntity player = source.getPlayerOrThrow();
                    MinecraftServer server = source.getServer();
                    PronounKey key = PronounKeyArgumentType.getPronounKey(ctx, "pronoun");

                    if (key.getTranslationKey() == null) {
                        // PronounDB has no translation key
                        setFromPronounDB(player, server, source);
                    } else {
                        // Custom pronoun translation keys
                        setCustom(player, key, server, source);
                    }

                    return 1;
                })
            )
        );
    }

    private void setFromPronounDB(ServerPlayerEntity player, MinecraftServer server, ServerCommandSource source) {
        if (PronounsPlease.pronoundb == null) {
            error("PronounDB is unavailable in offline mode", null, source);
            return;
        }

        PronounsPlease.pronoundb.lookupAsync(PronounDBClient.Platform.MINECRAFT, player.getUuid().toString())
            .thenAccept(pronouns -> pronouns.ifPresent(p ->
                server.execute(() -> {
                    String translatedPronouns = PronounsTranslationManager
                        .INSTANCE
                        .translate(player, p.asTranslationKeys().getFirst());

                    PronounsTeamManager.setPronouns(player, new PronounsSource.PronounDB(new WeakReference<>(p)), server);
                    PronounsTeamManager.syncToPlayer(player, server);
                    source.sendFeedback(
                        () -> PronounsCommandManager.SUCCESS_PREFIX.copy().append(
                            Text.literal("Pronouns set to ")
                                .formatted(Formatting.GRAY)
                                .append(Text.literal(translatedPronouns).formatted(Formatting.AQUA, Formatting.ITALIC))
                                .append(Text.literal(" from PrononunDB"))
                        ),
                        false
                    );
                })
            ))
            .exceptionally(e -> {
                Text why = Text.literal(e.getCause().getMessage()).formatted(Formatting.BOLD);
                server.execute(() -> error("Oh no! An error occurred while setting your pronouns: ", why, source));
                return null;
            });
    }

    private void setCustom(ServerPlayerEntity player, PronounKey key, MinecraftServer server, ServerCommandSource source) {
        PronounsTeamManager.setPronouns(player, new PronounsSource.Custom(key.getTranslationKey()), server);
        PronounsTeamManager.syncToPlayer(player, server);

        source.sendFeedback(
            () -> PronounsCommandManager.SUCCESS_PREFIX.copy().append(
                Text.literal("Pronouns set to ")
                    .formatted(Formatting.GRAY)
                    .append(Text.literal(key.asString()).formatted(Formatting.AQUA, Formatting.ITALIC))
            ),
            false
        );
    }

    protected static Text error(String message, @Nullable Text special, @Nullable ServerCommandSource source) {
        MutableText formattedMessage = Text.literal(message).formatted(Formatting.GRAY);
        if (special != null) formattedMessage.append(special);

        Text formatted = PronounsCommandManager.ERROR_PREFIX.copy().append(formattedMessage);
        if (source != null) {
            source.sendFeedback(() -> formatted, false);
        }

        return formatted;
    }
}
